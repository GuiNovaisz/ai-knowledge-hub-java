package dev.knowledgehub.citation;

import dev.knowledgehub.generation.*;
import dev.knowledgehub.shared.HubException;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class CitationValidator {
    public record Citation(String sourceId,UUID documentId,String filename,Integer page,UUID chunkId,String quote) {}
    public List<Citation> validate(UUID kb,GroundedChat.Draft draft,List<ContextAssembler.Source> sources) {
        if(draft==null || draft.status()==null || !Set.of("ANSWERED","SOURCE_CONFLICT","INSUFFICIENT_EVIDENCE").contains(draft.status()) || draft.claims()==null)
            throw invalid();
        if(draft.status().equals("INSUFFICIENT_EVIDENCE")) { if(!draft.claims().isEmpty()) throw invalid(); return List.of(); }
        if(draft.claims().isEmpty() || draft.claims().size()>20) throw invalid();
        Map<String,ContextAssembler.Source> allowed=new HashMap<>();
        sources.forEach(s->{ if(!s.hit().knowledgeBaseId().equals(kb)) throw invalid(); allowed.put(s.sourceId(),s); });
        Set<String> used=new LinkedHashSet<>();
        for(var claim:draft.claims()) {
            if(claim==null || claim.text()==null || claim.text().isBlank() || claim.text().length()>1500 || claim.sourceIds()==null || claim.sourceIds().isEmpty()) throw invalid();
            for(String id:claim.sourceIds()) { if(!allowed.containsKey(id)) throw invalid(); used.add(id); }
        }
        if(draft.status().equals("SOURCE_CONFLICT") && used.size()<2) throw invalid();
        return used.stream().map(id->{var h=allowed.get(id).hit(); return new Citation(id,h.documentId(),h.filename(),h.page(),h.chunkId(),h.text());}).toList();
    }
    private HubException invalid() { return new HubException("INVALID_MODEL_RESPONSE",502); }
}
