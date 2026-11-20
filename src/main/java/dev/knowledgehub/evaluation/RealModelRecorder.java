package dev.knowledgehub.evaluation;

import dev.knowledgehub.document.CorpusStore;
import dev.knowledgehub.ingestion.IngestionService;
import dev.knowledgehub.generation.*;
import dev.knowledgehub.retrieval.*;
import dev.knowledgehub.shared.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/** Opt-in recorder: no corpus edits, fixture substitutions or changes to ranking policy. */
@Component
@Profile("evaluation")
public class RealModelRecorder {
    private final CorpusStore store;
    private final IngestionService ingestion;
    private final ChunkRetriever retrieval;
    private final QueryService queries;
    private final ContextAssembler assembler;
    private final ChatModel model;
    private final ProviderExecutor executor;
    private final MeterRegistry meters;
    private final JdbcClient db;
    private final HubSettings settings;
    private final Path output;
    private final List<Map<String,Object>> rows=new ArrayList<>();
    private final Map<String,Object> report=new LinkedHashMap<>();
    public RealModelRecorder(CorpusStore store,IngestionService ingestion,ChunkRetriever retrieval,QueryService queries,
        ContextAssembler assembler,ChatModel model,ProviderExecutor executor,MeterRegistry meters,JdbcClient db,HubSettings settings,
        @Value("${hub.evaluation-output:target/evaluation/real-model.json}") String output) {
        this.store=store;this.ingestion=ingestion;this.retrieval=retrieval;this.queries=queries;this.assembler=assembler;
        this.model=model;this.executor=executor;this.meters=meters;this.db=db;this.settings=settings;this.output=Path.of(output);
    }
    public void record() throws Exception {
        if(db.sql("SELECT count(*) FROM knowledge_base").query(Long.class).single()!=0)
            throw new IllegalStateException("Real-model gate requires a clean isolated database");
        report.put("startedAt",Instant.now().toString());report.put("java",System.getProperty("java.version"));
        report.put("postgresql",db.sql("SHOW server_version").query(String.class).single());
        report.put("pgvector",db.sql("SELECT extversion FROM pg_extension WHERE extname='vector'").query(String.class).single());
        report.put("threshold",settings.minimumScore());report.put("rows",rows);
        UUID a=store.create("evaluation-A","Controlled real-model corpus").id();
        UUID b=store.create("evaluation-B","Isolated fictional secret").id();
        report.put("knowledgeBases",Map.of("A",a,"B",b));
        try(var files=Files.list(Path.of("evaluation/corpus"))) {
            for(Path file:files.sorted().toList()) {
                long start=System.nanoTime();
                var doc=ingestion.upload(file.getFileName().toString().equals("secret.md")?b:a,file.getFileName().toString(),"text/markdown",Files.readAllBytes(file));
                Map<String,Object> row=new LinkedHashMap<>();row.put("stage","ingestion");row.put("document",doc);row.put("elapsedMs",elapsed(start));rows.add(row);save();
                if(!doc.status().equals("READY")) throw new IllegalStateException("Fixture ingestion failed: "+doc.failureReason());
            }
        }
        var cases=new ObjectMapper().readValue(Path.of("evaluation/golden-questions.json").toFile(),RealModelEvaluation.Golden[].class);
        for(var strategy:ChunkRetriever.Strategy.values()) for(var g:cases) {
            UUID kb=g.knowledgeBase().equals("A")?a:b;long started=System.nanoTime();
            var hits=retrieval.retrieve(kb,g.query(),3,null,strategy);
            Map<String,Object> row=new LinkedHashMap<>();row.put("stage","retrieval");row.put("case",g.id());row.put("strategy",strategy);row.put("hits",hits);row.put("elapsedMs",elapsed(started));
            boolean isolated=hits.stream().allMatch(h->h.knowledgeBaseId().equals(kb));row.put("isolationPass",isolated);
            if(!g.relevantDocuments().isEmpty())row.put("metrics",RetrievalMetrics.score(hits.stream().map(ChunkRetriever.Hit::filename).toList(),new HashSet<>(g.relevantDocuments()),3));
            rows.add(row);save();if(!isolated)throw new IllegalStateException("CRITICAL cross-KB retrieval; evaluation stopped");
        }
        var judge=new RelevancyEvaluator(ChatClient.builder(model));
        for(var g:cases) {
            UUID kb=g.knowledgeBase().equals("A")?a:b;Map<String,Object> row=new LinkedHashMap<>();row.put("stage","generation");row.put("case",g.id());row.put("query",g.query());
            var inspected=retrieval.retrieve(kb,g.query(),5,null,ChunkRetriever.Strategy.VECTOR);
            var sources=assembler.assemble(kb,inspected);row.put("assembledSources",sources);
            double embeddingBefore=timer("hub.provider.latency","EMBEDDING"),modelBefore=timer("hub.provider.latency","MODEL"),retrievalBefore=timer("hub.retrieval.latency",null);
            long started=System.nanoTime();
            try {
                var answer=queries.query(kb,g.query(),5,null);row.put("answer",answer);
                row.put("answerabilityPass",answer.status().equals(g.expectedAnswerability()));
                row.put("expectedCitationsPass",answer.citations().stream().map(c->c.filename()).toList().containsAll(g.expectedCitations()));
                boolean citationPass=answer.citations().stream().allMatch(c->sources.stream().anyMatch(s->s.sourceId().equals(c.sourceId())&&s.hit().chunkId().equals(c.chunkId())&&s.hit().documentId().equals(c.documentId())&&s.hit().knowledgeBaseId().equals(kb)&&s.hit().text().equals(c.quote()))
                    &&db.sql("SELECT count(*) FROM chunk WHERE id=:id AND kb_id=:kb AND content=:quote").param("id",c.chunkId()).param("kb",kb).param("quote",c.quote()).query(Long.class).single()==1
                    &&answer.retrieval().stream().anyMatch(h->h.chunkId().equals(c.chunkId())));
                row.put("citationValidationPass",citationPass);
                boolean isolated=answer.retrieval().stream().allMatch(h->inspected.stream().anyMatch(i->i.chunkId().equals(h.chunkId())&&i.knowledgeBaseId().equals(kb)));
                row.put("isolationPass",isolated);row.put("injectionMarkerAbsent",!answer.answer().contains("BANANA"));
                if(!isolated){rows.add(row);save();throw new IsolationFailure();}
                recordLatency(row,started,embeddingBefore,modelBefore,retrievalBefore);
                if(!answer.citations().isEmpty()) {
                    List<Document> evidence=answer.citations().stream().map(c->new Document(c.quote())).toList();long judgeStarted=System.nanoTime();
                    try { row.put("modelBasedJudge",executor.call("JUDGE",()->judge.evaluate(new EvaluationRequest(g.query(),evidence,answer.answer())))); }
                    catch(RuntimeException e) { row.put("judgeError",code(e)); }
                    row.put("judgeMs",elapsed(judgeStarted));
                }
            } catch(IsolationFailure e){throw e;}
            catch(RuntimeException e){row.put("error",code(e));recordLatency(row,started,embeddingBefore,modelBefore,retrievalBefore);}
            rows.add(row);save();
            System.out.println("REAL_MODEL_CASE_COMPLETED "+g.id());
        }
        report.put("completedAt",Instant.now().toString());save();
    }
    private void recordLatency(Map<String,Object> row,long start,double embedding,double model,double retrievalTime) {
        row.put("queryMs",elapsed(start));row.put("embeddingMs",timer("hub.provider.latency","EMBEDDING")-embedding);
        row.put("generationMs",timer("hub.provider.latency","MODEL")-model);row.put("retrievalMs",timer("hub.retrieval.latency",null)-retrievalTime);
    }
    private double timer(String name,String stage){var search=meters.find(name);if(stage!=null)search.tags("stage",stage);var t=search.timer();return t==null?0:t.totalTime(TimeUnit.MILLISECONDS);}
    private static double elapsed(long start){return (System.nanoTime()-start)/1_000_000.0;}
    private static String code(RuntimeException e){return e instanceof HubException h?h.code():"EVALUATION_FAILURE";}
    private void save() throws Exception {Files.createDirectories(output.toAbsolutePath().getParent());new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(),report);}
    private static final class IsolationFailure extends RuntimeException {}
}
