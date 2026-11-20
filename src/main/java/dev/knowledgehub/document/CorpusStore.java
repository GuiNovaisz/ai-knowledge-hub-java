package dev.knowledgehub.document;

import java.util.*;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import dev.knowledgehub.shared.HubException;

@Repository
public class CorpusStore {
    public record KnowledgeBase(UUID id,String name,String description,OffsetDateTime createdAt) {}
    public record StoredDocument(UUID id,UUID knowledgeBaseId,String filename,String mediaType,String checksum,
        String status,String failureReason,OffsetDateTime createdAt,OffsetDateTime indexedAt,UUID activeVersion) {}
    public record Reservation(StoredDocument document,boolean created) {}
    private final JdbcClient db;
    public CorpusStore(JdbcClient db) { this.db=db; }
    public KnowledgeBase create(String name,String description) {
        UUID id=UUID.randomUUID();
        db.sql("INSERT INTO knowledge_base(id,name,description) VALUES(:id,:name,:description)")
            .param("id",id).param("name",name).param("description",description).update();
        return kb(id);
    }
    public KnowledgeBase kb(UUID id) {
        return db.sql("SELECT id,name,description,created_at FROM knowledge_base WHERE id=:id").param("id",id)
            .query(KnowledgeBase.class).optional().orElseThrow(()->new HubException("KB_NOT_FOUND",404));
    }
    private static final String FIELDS="id,kb_id AS knowledge_base_id,filename,media_type,checksum,status,failure_reason,created_at,indexed_at,active_version";
    public StoredDocument document(UUID kb,UUID id) {
        return db.sql("SELECT "+FIELDS+" FROM document WHERE kb_id=:kb AND id=:id").param("kb",kb).param("id",id)
            .query(StoredDocument.class).optional().orElseThrow(()->new HubException("DOCUMENT_NOT_FOUND",404));
    }
    public List<StoredDocument> documents(UUID kb) {
        kb(kb);
        return db.sql("SELECT "+FIELDS+" FROM document WHERE kb_id=:kb ORDER BY created_at DESC LIMIT 100")
            .param("kb",kb).query(StoredDocument.class).list();
    }
    public Reservation reserve(UUID kb,String filename,String type,String checksum,byte[] original) {
        kb(kb); UUID id=UUID.randomUUID();
        int inserted=db.sql("INSERT INTO document(id,kb_id,filename,media_type,checksum,original,status) VALUES(:id,:kb,:name,:type,:sum,:bytes,'UPLOADED') ON CONFLICT(kb_id,checksum) DO NOTHING")
            .param("id",id).param("kb",kb).param("name",filename).param("type",type).param("sum",checksum).param("bytes",original).update();
        var document=db.sql("SELECT "+FIELDS+" FROM document WHERE kb_id=:kb AND checksum=:sum").param("kb",kb).param("sum",checksum)
            .query(StoredDocument.class).single();
        return new Reservation(document,inserted==1);
    }
    public byte[] original(UUID kb,UUID id) {
        return db.sql("SELECT original FROM document WHERE kb_id=:kb AND id=:id").param("kb",kb).param("id",id).query(byte[].class).single();
    }
    public UUID claim(UUID kb,UUID id) {
        UUID attempt=UUID.randomUUID();
        int changed=db.sql("UPDATE document SET status='PROCESSING',attempt_version=:v,attempt_at=now(),failure_reason=NULL WHERE kb_id=:kb AND id=:id AND (status<>'PROCESSING' OR attempt_at < now()-interval '15 minutes')")
            .param("v",attempt).param("kb",kb).param("id",id).update();
        if(changed!=1) throw new HubException("INGESTION_IN_PROGRESS",409);
        return attempt;
    }
    public void failed(UUID id,UUID attempt,String reason) {
        db.sql("UPDATE document SET status='FAILED',failure_reason=:reason WHERE id=:id AND attempt_version=:v")
            .param("reason",reason).param("id",id).param("v",attempt).update();
    }
}
