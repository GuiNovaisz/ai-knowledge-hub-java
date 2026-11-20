package dev.knowledgehub;

import dev.knowledgehub.document.*;
import dev.knowledgehub.ingestion.*;
import dev.knowledgehub.embedding.*;
import dev.knowledgehub.generation.*;
import dev.knowledgehub.retrieval.*;
import dev.knowledgehub.shared.*;
import dev.knowledgehub.evaluation.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class GroundedRagIT {
    @Container static PostgreSQLContainer postgres=new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:0.8.7-pg17-bookworm").asCompatibleSubstituteFor("postgres"));
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);
    }
    @MockitoBean EmbeddingProvider embeddings;
    @MockitoBean GroundedChat chat;
    @Autowired CorpusStore store;
    @Autowired IngestionService ingestion;
    @Autowired ChunkRetriever retrieval;
    @Autowired QueryService queries;
    @Autowired JdbcClient db;
    @Value("${local.server.port}") int port;
    UUID kb;
    @BeforeEach void setup() {
        db.sql("TRUNCATE chunk,document,knowledge_base CASCADE").update();reset(embeddings,chat);
        when(embeddings.provider()).thenReturn("deterministic-fixture");when(embeddings.model()).thenReturn("concept-v1-768");
        when(embeddings.embed(anyString(),anyBoolean())).thenAnswer(call->fixtureVector(call.getArgument(0)));
        when(chat.generate(anyString(),anyList())).thenAnswer(call->{
            List<ContextAssembler.Source> sources=call.getArgument(1);
            boolean conflict=sources.stream().filter(s->s.hit().filename().startsWith("retention-")).count()>1;
            var claims=sources.stream().map(s->new GroundedChat.Claim(s.hit().text(),List.of(s.sourceId()))).toList();
            return new GroundedChat.Completion(new GroundedChat.Draft(conflict ? "SOURCE_CONFLICT" : "ANSWERED",claims),"deterministic-chat",0);
        });
        kb=store.create("A","Test corpus").id();
    }
    static float[] fixtureVector(String text) {
        String t=text.toLowerCase(Locale.ROOT);float[] v=new float[768];
        if(t.startsWith("# support")) v[2]=1;
        else {
            if(t.contains("refund")||t.contains("return")||t.contains("send back"))v[0]=1;
            if(t.contains("shipping")||t.contains("delivery"))v[1]=1;
            if(t.contains("contact support"))v[2]=1;
            if(t.contains("retention"))v[3]=1;
            if(t.contains("orchard")||t.contains("apples"))v[4]=1;
            if(t.contains("secret")||t.contains("codename"))v[5]=1;
            if(t.contains("uptime"))v[6]=1;
        }
        boolean empty=true;for(float f:v) if(f!=0)empty=false;
        if(empty)v[15]=1;
        return v;
    }
    CorpusStore.StoredDocument upload(UUID base,String name,String text) { return ingestion.upload(base,name,"text/markdown",text.getBytes(StandardCharsets.UTF_8)); }
    CorpusStore.StoredDocument corpus(UUID base,String name) throws Exception { return upload(base,name,Files.readString(Path.of("evaluation/corpus",name))); }
    @Test void indexesRealVectorsAndEnforcesKbAndDocumentFilters() {
        var a=upload(kb,"a.md","Refund window is thirty days.");
        UUID other=store.create("B","Isolated").id();var b=upload(other,"b.md","Secret codename ORCHID-947.");
        assertThat(a.status()).isEqualTo("READY");assertThat(b.status()).isEqualTo("READY");
        var hits=retrieval.retrieve(kb,"refund window",5,null,ChunkRetriever.Strategy.VECTOR);
        assertThat(hits.getFirst().documentId()).isEqualTo(a.id()); assertThat(hits).allMatch(h->h.knowledgeBaseId().equals(kb));
        assertThat(retrieval.retrieve(kb,"secret codename",5,b.id(),ChunkRetriever.Strategy.VECTOR)).isEmpty();
        assertThat(queries.query(kb,"secret codename",5,null).status()).isEqualTo("INSUFFICIENT_EVIDENCE");verifyNoInteractions(chat);
        assertThat(queries.query(other,"secret codename",5,null).answer()).contains("ORCHID-947");
    }
    @Test void duplicateUploadsAreIdempotentUnderConcurrency() throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            CountDownLatch start=new CountDownLatch(1);
            Callable<UUID> task=()->{start.await();return upload(kb,"same.md","Refund window is thirty days.").id();};
            Future<UUID> a=pool.submit(task),b=pool.submit(task);start.countDown();
            assertThat(a.get(20,TimeUnit.SECONDS)).isEqualTo(b.get(20,TimeUnit.SECONDS));
        }
        assertThat(store.documents(kb)).hasSize(1);
        assertThat(db.sql("SELECT count(*) FROM chunk").query(Long.class).single()).isEqualTo(1);
    }
    @Test void embeddingFailurePublishesNoPartialChunksAndRetryReplacesVersion() {
        AtomicInteger calls=new AtomicInteger();
        when(embeddings.embed(anyString(),anyBoolean())).thenAnswer(c->{ if(calls.incrementAndGet()==2)throw new HubException("EMBEDDING_TIMEOUT",504);return fixtureVector(c.getArgument(0)); });
        var failed=upload(kb,"long.md","Refund window thirty days. ".repeat(130));
        assertThat(failed.status()).isEqualTo("FAILED");assertThat(failed.failureReason()).isEqualTo("EMBEDDING_TIMEOUT");
        assertThat(db.sql("SELECT count(*) FROM chunk").query(Long.class).single()).isZero();
        assertThat(retrieval.retrieve(kb,"refund",5,null,ChunkRetriever.Strategy.VECTOR)).isEmpty();
        when(embeddings.embed(anyString(),anyBoolean())).thenAnswer(c->fixtureVector(c.getArgument(0)));
        var ready=ingestion.reindex(kb,failed.id()); assertThat(ready.status()).isEqualTo("READY");
        var next=ingestion.reindex(kb,failed.id());assertThat(next.activeVersion()).isNotEqualTo(ready.activeVersion());
        assertThat(db.sql("SELECT count(DISTINCT ingestion_version) FROM chunk").query(Long.class).single()).isEqualTo(1);
    }
    @Test void indexingTransactionRollsBackWhenActivationFails() {
        var doc=upload(kb,"atomic.md","Refund window is thirty days.");UUID old=doc.activeVersion();
        db.sql("CREATE FUNCTION reject_activation() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.status='READY' THEN RAISE EXCEPTION 'test activation failure'; END IF; RETURN NEW; END $$").update();
        db.sql("CREATE TRIGGER reject_activation BEFORE UPDATE ON document FOR EACH ROW EXECUTE FUNCTION reject_activation()").update();
        try {
            var failed=ingestion.reindex(kb,doc.id()); assertThat(failed.status()).isEqualTo("FAILED");
            assertThat(db.sql("SELECT DISTINCT ingestion_version FROM chunk").query(UUID.class).single()).isEqualTo(old);
            assertThat(retrieval.retrieve(kb,"refund",3,null,ChunkRetriever.Strategy.VECTOR)).isEmpty();
        } finally { db.sql("DROP TRIGGER reject_activation ON document").update();db.sql("DROP FUNCTION reject_activation()").update(); }
    }
    @Test void staleAttemptCanRecoverButCannotPublishOverNewVersion() {
        var doc=upload(kb,"retry.md","Refund window is thirty days.");
        UUID old=store.claim(kb,doc.id());
        assertThatThrownBy(()->ingestion.reindex(kb,doc.id())).isInstanceOfSatisfying(HubException.class,e->assertThat(e.code()).isEqualTo("INGESTION_IN_PROGRESS"));
        db.sql("UPDATE document SET attempt_at=now()-interval '16 minutes' WHERE id=:id").param("id",doc.id()).update();
        var next=ingestion.reindex(kb,doc.id());store.failed(doc.id(),old,"STALE_FAILURE");
        assertThat(store.document(kb,doc.id()).status()).isEqualTo("READY");assertThat(next.activeVersion()).isNotEqualTo(old);
    }
    @Test void invalidCitationAndProviderFailureArePredictableAndDoNotLoseCorpus() {
        var doc=upload(kb,"a.md","Refund window thirty days.");
        when(chat.generate(anyString(),anyList())).thenReturn(new GroundedChat.Completion(new GroundedChat.Draft("ANSWERED",List.of(new GroundedChat.Claim("Fake",List.of("S900")))),"fake",0));
        assertThatThrownBy(()->queries.query(kb,"refund",5,null)).isInstanceOfSatisfying(HubException.class,e->assertThat(e.code()).isEqualTo("INVALID_MODEL_RESPONSE"));
        when(chat.generate(anyString(),anyList())).thenThrow(new HubException("MODEL_UNAVAILABLE",503));
        assertThatThrownBy(()->queries.query(kb,"refund",5,null)).isInstanceOfSatisfying(HubException.class,e->assertThat(e.status()).isEqualTo(503));
        assertThat(store.document(kb,doc.id()).status()).isEqualTo("READY");
    }
    @Test void webApiRejectsInvalidTopKAndDoesNotExposeDatabaseErrors() throws Exception {
        HttpClient client=HttpClient.newHttpClient();
        var invalid=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/knowledge-bases/"+kb+"/query"))
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"refund\",\"topK\":200}")).build();
        var response=client.send(invalid,HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(400);assertThat(response.body()).contains("INVALID_INPUT");assertThat(response.headers().firstValue("X-Correlation-ID")).isPresent();
        var notFound=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/knowledge-bases/"+UUID.randomUUID())).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(notFound.statusCode()).isEqualTo(404);assertThat(notFound.body()).doesNotContain("SELECT");
        var openApi=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/v3/api-docs")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(openApi.statusCode()).isEqualTo(200);assertThat(openApi.body()).contains("0.1.0-dev","Citation","StoredDocument","/api/v1/knowledge-bases/{id}/query");
    }
    @Test void webApiSupportsUploadRetrievalAnswerAndAbstention() throws Exception {
        HttpClient client=HttpClient.newHttpClient();String base="http://127.0.0.1:"+port+"/api/v1/knowledge-bases/"+kb;
        String boundary="KnowledgeHubTestBoundary";
        String body="--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"policy.md\"\r\nContent-Type: text/markdown\r\n\r\nRefund window is thirty days.\r\n--"+boundary+"--\r\n";
        var uploaded=client.send(HttpRequest.newBuilder(URI.create(base+"/documents")).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(uploaded.statusCode()).isEqualTo(200);assertThat(uploaded.body()).contains("READY","policy.md");
        String id=new ObjectMapper().readTree(uploaded.body()).get("id").asString();
        var detail=client.send(HttpRequest.newBuilder(URI.create(base+"/documents/"+id)).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(detail.statusCode()).isEqualTo(200);assertThat(detail.body()).doesNotContain("original");
        var answered=client.send(HttpRequest.newBuilder(URI.create(base+"/query")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"refund window\",\"topK\":5}")).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(answered.statusCode()).isEqualTo(200);assertThat(answered.body()).contains("ANSWERED","citations","thirty days");
        var missing=client.send(HttpRequest.newBuilder(URI.create(base+"/query")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"lunar mission budget\",\"topK\":5}")).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(missing.statusCode()).isEqualTo(200);assertThat(missing.body()).contains("INSUFFICIENT_EVIDENCE");
        var inspected=client.send(HttpRequest.newBuilder(URI.create(base+"/retrieval")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"refund\",\"topK\":3,\"strategy\":\"HYBRID\"}")).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(inspected.statusCode()).isEqualTo(200);assertThat(inspected.body()).contains("rankScore","policy.md");
    }
    record Golden(String id,String query,String knowledgeBase,List<String> relevantDocuments,List<String> relevantChunks,String expectedAnswerability,List<String> expectedCitations) {}
    @Test void goldenDatasetProducesReproducibleBaselineAndPassesDeterministicGates() throws Exception {
        UUID b=store.create("B","Secret fictional corpus").id();
        try(var files=Files.list(Path.of("evaluation/corpus"))) {
            for(Path file:files.sorted().toList()) corpus(file.getFileName().toString().equals("secret.md") ? b : kb,file.getFileName().toString());
        }
        var cases=new ObjectMapper().readValue(Path.of("evaluation/golden-questions.json").toFile(),Golden[].class);
        int k=3;StringBuilder report=new StringBuilder("# Retrieval baseline — deterministic fixture\n\n");
        report.append("Dataset: all 10 golden cases; document-level K=3; 8 answerable cases in retrieval macro averages. Two negative cases are evaluated by abstention gates.\n\n");
        report.append("Embedding: concept-v1-768, synthetic deterministic test double. Chat: deterministic source-copying fixture. These results validate SQL/fusion mechanics, **not real model semantic quality**.\n\n");
        report.append("Database: ").append(db.sql("SHOW server_version").query(String.class).single()).append("; pgvector: ").append(db.sql("SELECT extversion FROM pg_extension WHERE extname='vector'").query(String.class).single()).append(".\n\n");
        report.append("| Strategy | Recall@3 | Precision@3 | MRR | Hit rate |\n|---|---:|---:|---:|---:|\n");
        for(var strategy:ChunkRetriever.Strategy.values()) {
            List<RetrievalMetrics.Score> scores=new ArrayList<>();
            for(var g:cases) {
                var hits=retrieval.retrieve(g.knowledgeBase().equals("A") ? kb : b,g.query(),k,null,strategy);
                assertThat(hits).allMatch(h->h.knowledgeBaseId().equals(g.knowledgeBase().equals("A") ? kb : b));
                if(!g.relevantDocuments().isEmpty()) scores.add(RetrievalMetrics.score(hits.stream().map(ChunkRetriever.Hit::filename).toList(),new HashSet<>(g.relevantDocuments()),k));
            }
            assertThat(scores.stream().mapToDouble(RetrievalMetrics.Score::hitRate).average().orElseThrow()).as(strategy.name()+" is operational").isGreaterThan(0);
            report.append(String.format(Locale.ROOT,"| %s | %.4f | %.4f | %.4f | %.4f |%n",strategy,scores.stream().mapToDouble(RetrievalMetrics.Score::recall).average().orElseThrow(),scores.stream().mapToDouble(RetrievalMetrics.Score::precision).average().orElseThrow(),scores.stream().mapToDouble(RetrievalMetrics.Score::reciprocalRank).average().orElseThrow(),scores.stream().mapToDouble(RetrievalMetrics.Score::hitRate).average().orElseThrow()));
        }
        for(var g:cases) {
            var result=queries.query(g.knowledgeBase().equals("A") ? kb : b,g.query(),5,null);
            assertThat(result.status()).as(g.id()).isEqualTo(g.expectedAnswerability());
            assertThat(result.citations().stream().map(c->c.filename()).toList()).as(g.id()).containsAll(g.expectedCitations());
            assertThat(result.answer()).doesNotContain("BANANA");
        }
        report.append("\nAll golden answerability/citation/isolation/injection fixture gates passed. No learned reranker is enabled. Vector remains the generation default until real-model evaluation demonstrates a better choice.\n");
        Files.createDirectories(Path.of("target/evaluation"));Files.writeString(Path.of("target/evaluation/baseline.md"),report.toString());
    }
}
