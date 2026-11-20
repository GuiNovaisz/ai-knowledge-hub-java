package dev.knowledgehub.retrieval;
import java.util.List;
public interface DocumentReranker {
    List<ChunkRetriever.Hit> rerank(String query,List<ChunkRetriever.Hit> hits);
}
