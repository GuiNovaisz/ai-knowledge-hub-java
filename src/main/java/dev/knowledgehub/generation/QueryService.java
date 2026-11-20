package dev.knowledgehub.generation;

import dev.knowledgehub.retrieval.*;
import dev.knowledgehub.citation.CitationValidator;
import dev.knowledgehub.citation.CitationValidator.Citation;
import java.util.*;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.MeterRegistry;

@Service
public class QueryService {
    public record RetrievalMetadata(int rank,UUID chunkId,UUID documentId,String filename,Integer page,double score,double rankScore) {}
    public record Answer(String status,String answer,List<Citation> citations,List<RetrievalMetadata> retrieval,
        String model,Integer usage,String correlationId) {}
    private final ChunkRetriever retriever;
    private final DocumentReranker reranker;
    private final ContextAssembler assembler;
    private final GroundedChat chat;
    private final CitationValidator citations;
    private final MeterRegistry metrics;
    public QueryService(ChunkRetriever retriever,DocumentReranker reranker,ContextAssembler assembler,GroundedChat chat,CitationValidator citations,MeterRegistry metrics) {
        this.retriever=retriever;this.reranker=reranker;this.assembler=assembler;this.chat=chat;this.citations=citations;this.metrics=metrics;
    }
    public Answer query(UUID kb,String question,int topK,UUID documentId) {
        MDC.put("knowledgeBaseId",kb.toString()); MDC.put("queryId",UUID.randomUUID().toString());
        try {
            var hits=retriever.retrieve(kb,question,topK,documentId,ChunkRetriever.Strategy.VECTOR);
            List<RetrievalMetadata> metadata=new ArrayList<>();
            for(int n=0;n<hits.size();n++) { var h=hits.get(n); metadata.add(new RetrievalMetadata(n+1,h.chunkId(),h.documentId(),h.filename(),h.page(),h.score(),h.rankScore())); }
            var sources=assembler.assemble(kb,reranker.rerank(question,hits));
            if(sources.isEmpty()) return abstain(metadata,null,null);
            var completion=chat.generate(question,sources);
            List<Citation> valid;
            try { valid=citations.validate(kb,completion.draft(),sources); }
            catch(RuntimeException e) { metrics.counter("hub.citation.invalid").increment(); throw e; }
            if(completion.draft().status().equals("INSUFFICIENT_EVIDENCE")) return abstain(metadata,completion.model(),completion.totalTokens());
            metrics.counter("hub.answers","status",completion.draft().status()).increment();
            if(completion.totalTokens()!=null) metrics.counter("hub.tokens").increment(completion.totalTokens());
            String answer=completion.draft().claims().stream().map(c->c.text()+" ["+String.join(",",c.sourceIds())+"]").reduce("",(a,b)->a.isEmpty() ? b : a+"\n"+b);
            return new Answer(completion.draft().status(),answer,valid,metadata,completion.model(),completion.totalTokens(),MDC.get("correlationId"));
        } finally { MDC.remove("knowledgeBaseId"); MDC.remove("queryId"); }
    }
    private Answer abstain(List<RetrievalMetadata> metadata,String model,Integer usage) {
        metrics.counter("hub.abstentions").increment();
        return new Answer("INSUFFICIENT_EVIDENCE","Não encontrei evidência suficiente na base selecionada para responder com segurança.",List.of(),metadata,model,usage,MDC.get("correlationId"));
    }
}
