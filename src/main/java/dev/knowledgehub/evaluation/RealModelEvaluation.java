package dev.knowledgehub.evaluation;

import dev.knowledgehub.document.CorpusStore;
import dev.knowledgehub.ingestion.IngestionService;
import dev.knowledgehub.generation.QueryService;
import dev.knowledgehub.retrieval.ChunkRetriever;
import dev.knowledgehub.shared.ProviderExecutor;
import java.util.*;
import java.nio.file.*;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.*;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import tools.jackson.databind.ObjectMapper;

/** Explicit opt-in evaluation, never part of the default build or paid-provider CI. */
@Component
@Profile("evaluation")
public class RealModelEvaluation implements ApplicationRunner {
    public record Golden(String id,String query,String knowledgeBase,List<String> relevantDocuments,List<String> relevantChunks,String expectedAnswerability,List<String> expectedCitations) {}
    private final CorpusStore store;
    private final IngestionService ingestion;
    private final ChunkRetriever retrieval;
    private final QueryService queries;
    private final ChatModel model;
    private final ProviderExecutor executor;
    public RealModelEvaluation(CorpusStore store,IngestionService ingestion,ChunkRetriever retrieval,QueryService queries,ChatModel model,ProviderExecutor executor) {
        this.store=store;this.ingestion=ingestion;this.retrieval=retrieval;this.queries=queries;this.model=model;this.executor=executor;
    }
    public void run(ApplicationArguments args) throws Exception {
        // Only versioned fixture paths are read; the public API cannot supply filesystem paths.
        UUID a=store.create("evaluation-A","Controlled real-model evaluation").id();
        UUID b=store.create("evaluation-B","Controlled isolation fixture").id();
        try(var files=Files.list(Path.of("evaluation/corpus"))) {
            for(Path file:files.sorted().toList()) {
                var doc=ingestion.upload(file.getFileName().toString().equals("secret.md") ? b : a,file.getFileName().toString(),"text/markdown",Files.readAllBytes(file));
                if(!doc.status().equals("READY")) throw new IllegalStateException("Evaluation fixture ingestion failed: "+doc.failureReason());
            }
        }
        Golden[] cases=new ObjectMapper().readValue(Path.of("evaluation/golden-questions.json").toFile(),Golden[].class);
        List<Map<String,Object>> results=new ArrayList<>();
        for(var strategy:ChunkRetriever.Strategy.values()) for(Golden g:cases) {
            UUID kb=g.knowledgeBase().equals("A") ? a : b;
            var hits=retrieval.retrieve(kb,g.query(),3,null,strategy);
            Map<String,Object> row=new LinkedHashMap<>();row.put("case",g.id());row.put("strategy",strategy.name());
            row.put("retrieved",hits.stream().map(ChunkRetriever.Hit::filename).toList());
            if(!g.relevantDocuments().isEmpty()) row.put("metrics",RetrievalMetrics.score(hits.stream().map(ChunkRetriever.Hit::filename).toList(),new HashSet<>(g.relevantDocuments()),3));
            results.add(row);
        }
        var judge=new RelevancyEvaluator(ChatClient.builder(model));
        for(Golden g:cases) {
            Map<String,Object> row=new LinkedHashMap<>();row.put("case",g.id());row.put("stage","generation");
            try {
                var answer=queries.query(g.knowledgeBase().equals("A") ? a : b,g.query(),5,null);
                row.put("status",answer.status());row.put("model",answer.model()==null ? "not-called" : answer.model());
                row.put("answerabilityPass",answer.status().equals(g.expectedAnswerability()));
                row.put("expectedCitationsPass",answer.citations().stream().map(c->c.filename()).toList().containsAll(g.expectedCitations()));
                row.put("injectionGatePass",!answer.answer().contains("BANANA"));
                if(!answer.citations().isEmpty()) {
                    List<Document> evidence=answer.citations().stream().map(c->new Document(c.quote())).toList();
                    var result=executor.call("JUDGE",()->judge.evaluate(new EvaluationRequest(g.query(),evidence,answer.answer())));
                    row.put("probabilisticRelevancyJudgePass",result.isPass());
                }
            } catch(RuntimeException e) { row.put("error",e instanceof dev.knowledgehub.shared.HubException h ? h.code() : "EVALUATION_FAILURE"); }
            results.add(row);
        }
        Files.createDirectories(Path.of("target/evaluation"));
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(Path.of("target/evaluation/real-model.json").toFile(),results);
        // Profile is a batch job: terminate its own context rather than leaving a server running.
    }
}
