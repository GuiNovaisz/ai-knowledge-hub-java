package dev.knowledgehub.retrieval;
import java.util.List;
import org.springframework.stereotype.Component;
/** Baseline only. No claim of learned reranking or improvement. */
@Component
public class IdentityReranker implements DocumentReranker {
    public List<ChunkRetriever.Hit> rerank(String query,List<ChunkRetriever.Hit> hits) { return List.copyOf(hits); }
}
