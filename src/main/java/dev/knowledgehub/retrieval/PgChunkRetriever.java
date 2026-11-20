package dev.knowledgehub.retrieval;

import dev.knowledgehub.document.CorpusStore;
import dev.knowledgehub.embedding.EmbeddingProvider;
import dev.knowledgehub.shared.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class PgChunkRetriever implements ChunkRetriever {
    private final JdbcClient db;
    private final CorpusStore store;
    private final EmbeddingProvider embeddings;
    private final HubSettings settings;
    private final MeterRegistry metrics;
    public PgChunkRetriever(JdbcClient db,CorpusStore store,EmbeddingProvider embeddings,HubSettings settings,MeterRegistry metrics) {
        this.db=db;this.store=store;this.embeddings=embeddings;this.settings=settings;this.metrics=metrics;
    }
    @Override public List<Hit> retrieve(UUID kb,String query,int topK,UUID documentId,Strategy strategy) {
        if(topK<1 || topK>20 || query==null || query.isBlank() || query.length()>2000) throw new HubException("INVALID_QUERY",400);
        query=QueryProcessor.normalize(query);
        store.kb(kb); long started=System.nanoTime();
        try {
            // Even lexical candidates carry semantic similarity for the generation evidence gate.
            String vector=Arrays.toString(embeddings.embed(query,true));
            List<Hit> semantic=select(kb,query,vector,documentId,false,Math.min(40,topK*3));
            List<Hit> result;
            if(strategy==Strategy.VECTOR) result=semantic;
            else {
                List<Hit> lexical=select(kb,query,vector,documentId,true,Math.min(40,topK*3));
                result=strategy==Strategy.LEXICAL ? lexical : fuse(semantic,lexical);
            }
            result=result.stream().limit(topK).toList(); metrics.summary("hub.retrieval.candidates").record(result.size()); return result;
        } catch(org.springframework.dao.DataAccessException e) { throw new HubException("RETRIEVAL_FAILURE",503); }
        finally { metrics.timer("hub.retrieval.latency").record(System.nanoTime()-started,TimeUnit.NANOSECONDS); }
    }
    private List<Hit> select(UUID kb,String query,String vector,UUID doc,boolean lexical,int limit) {
        String sql="""
            SELECT c.id AS chunk_id,c.kb_id AS knowledge_base_id,c.document_id,d.filename,c.page,c.chunk_index,
              c.heading,c.ingestion_version,c.content AS text,1-(c.embedding <=> CAST(:vector AS vector)) AS score,
            """+(lexical ? "ts_rank_cd(c.search_vector,websearch_to_tsquery('simple',:query))" : "1-(c.embedding <=> CAST(:vector AS vector))")+"""
             AS rank_score FROM chunk c JOIN document d ON d.id=c.document_id AND d.kb_id=c.kb_id
             WHERE c.kb_id=:kb AND d.kb_id=:kb AND d.status='READY' AND c.ingestion_version=d.active_version
               AND c.embedding_version=:ev
            """+(doc==null ? "" : " AND c.document_id=:doc ")+
            (lexical ? " AND c.search_vector @@ websearch_to_tsquery('simple',:query) " : "")+
            " ORDER BY rank_score DESC,c.id LIMIT :lim";
        var statement=db.sql(sql).param("vector",vector).param("kb",kb).param("ev",settings.embeddingVersion()).param("lim",limit);
        if(lexical) statement.param("query",QueryProcessor.lexical(query));
        if(doc!=null) statement.param("doc",doc);
        return statement.query(Hit.class).list();
    }
    public static List<Hit> fuse(List<Hit> vector,List<Hit> lexical) {
        Map<UUID,Hit> hits=new HashMap<>(); Map<UUID,Double> scores=new HashMap<>();
        for(var list:List.of(vector,lexical)) for(int rank=0;rank<list.size();rank++) {
            Hit h=list.get(rank); hits.put(h.chunkId(),h); scores.merge(h.chunkId(),1.0/(60+rank+1),Double::sum);
        }
        return hits.values().stream().map(h->new Hit(h.chunkId(),h.knowledgeBaseId(),h.documentId(),h.filename(),h.page(),h.chunkIndex(),h.heading(),h.ingestionVersion(),h.text(),h.score(),scores.get(h.chunkId())))
            .sorted(Comparator.comparingDouble(Hit::rankScore).reversed().thenComparing(Hit::chunkId)).toList();
    }
}
