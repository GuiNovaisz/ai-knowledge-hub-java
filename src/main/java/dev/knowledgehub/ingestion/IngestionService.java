package dev.knowledgehub.ingestion;

import dev.knowledgehub.document.CorpusStore;
import dev.knowledgehub.document.CorpusStore.StoredDocument;
import dev.knowledgehub.chunking.ParagraphChunker;
import dev.knowledgehub.embedding.*;
import dev.knowledgehub.shared.*;
import java.util.*;
import java.security.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.*;
import io.micrometer.core.instrument.MeterRegistry;

@Service
public class IngestionService {
    private final CorpusStore store;
    private final DocumentParser parser;
    private final ParagraphChunker chunker;
    private final EmbeddingProvider embeddings;
    private final JdbcClient db;
    private final TransactionTemplate tx;
    private final HubSettings settings;
    private final MeterRegistry metrics;
    private static final Logger log=LoggerFactory.getLogger(IngestionService.class);
    public IngestionService(CorpusStore store,DocumentParser parser,ParagraphChunker chunker,EmbeddingProvider embeddings,
        JdbcClient db,TransactionTemplate tx,HubSettings settings,MeterRegistry metrics) {
        this.store=store;this.parser=parser;this.chunker=chunker;this.embeddings=embeddings;this.db=db;this.tx=tx;this.settings=settings;this.metrics=metrics;
    }
    public StoredDocument upload(UUID kb,String filename,String declared,byte[] bytes) {
        String name=SafeDocumentParser.safeFilename(filename);
        String type=SafeDocumentParser.validate(bytes,name,declared);
        String checksum;
        try { checksum=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        var reservation=store.reserve(kb,name,type,checksum,bytes);
        StoredDocument doc=reservation.document();
        // Existing identities (including FAILED/PROCESSING) are returned; retry is explicit.
        if(!reservation.created()) return doc;
        try { return reindex(kb,doc.id()); }
        catch(HubException e) { if(e.code().equals("INGESTION_IN_PROGRESS")) return store.document(kb,doc.id()); throw e; }
    }
    public StoredDocument reindex(UUID kb,UUID id) {
        StoredDocument doc=store.document(kb,id); UUID version=store.claim(kb,id);
        MDC.put("knowledgeBaseId",kb.toString()); MDC.put("documentId",id.toString());
        try {
            var pieces=chunker.split(parser.parse(store.original(kb,id),doc.mediaType()));
            List<float[]> vectors=new ArrayList<>();
            for(var piece:pieces) {
                float[] v=embeddings.embed(piece.text(),false); SpringAiEmbeddingProvider.validate(v,settings.dimensions()); vectors.add(v);
            }
            tx.executeWithoutResult(status->{
                for(int i=0;i<pieces.size();i++) {
                    var p=pieces.get(i);
                    db.sql("INSERT INTO chunk(id,kb_id,document_id,ingestion_version,chunk_index,page,heading,content,embedding,embedding_provider,embedding_model,embedding_version,dimensions) VALUES(:id,:kb,:doc,:v,:idx,:page,:heading,:text,CAST(:vector AS vector),:provider,:model,:ev,:dim)")
                        .param("id",UUID.randomUUID()).param("kb",kb).param("doc",id).param("v",version).param("idx",p.index()).param("page",p.page())
                        .param("heading",p.heading()).param("text",p.text()).param("vector",Arrays.toString(vectors.get(i)))
                        .param("provider",embeddings.provider()).param("model",embeddings.model()).param("ev",settings.embeddingVersion()).param("dim",settings.dimensions()).update();
                }
                int updated=db.sql("UPDATE document SET status='READY',active_version=:v,indexed_at=now(),embedding_version=:ev WHERE id=:id AND attempt_version=:v AND status='PROCESSING'")
                    .param("v",version).param("ev",settings.embeddingVersion()).param("id",id).update();
                if(updated!=1) throw new HubException("INGESTION_CONFLICT",409);
                db.sql("DELETE FROM chunk WHERE document_id=:id AND ingestion_version<>:v").param("id",id).param("v",version).update();
            });
            metrics.counter("hub.documents.ingested").increment(); metrics.counter("hub.chunks.generated").increment(pieces.size());
            log.info("Document indexed");
        } catch(RuntimeException e) {
            store.failed(id,version,e instanceof HubException h ? h.code() : "INDEXING_FAILURE");
            metrics.counter("hub.ingestion.failures").increment(); log.warn("Document ingestion failed");
        } finally { MDC.remove("knowledgeBaseId"); MDC.remove("documentId"); }
        return store.document(kb,id);
    }
}
