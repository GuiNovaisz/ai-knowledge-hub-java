package dev.knowledgehub.retrieval;

import java.util.*;
public interface ChunkRetriever {
    enum Strategy { VECTOR, LEXICAL, HYBRID }
    record Hit(UUID chunkId,UUID knowledgeBaseId,UUID documentId,String filename,Integer page,
        int chunkIndex,String heading,UUID ingestionVersion,String text,double score,double rankScore) {}
    List<Hit> retrieve(UUID kb,String query,int topK,UUID documentId,Strategy strategy);
}
