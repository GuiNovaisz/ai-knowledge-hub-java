package dev.knowledgehub.generation;

import dev.knowledgehub.retrieval.ChunkRetriever.Hit;
import dev.knowledgehub.shared.HubSettings;
import dev.knowledgehub.security.EvidenceFilter;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ContextAssembler {
    public record Source(String sourceId,Hit hit) {}
    private final HubSettings settings;
    private final EvidenceFilter filter;
    public ContextAssembler(HubSettings settings,EvidenceFilter filter) { this.settings=settings;this.filter=filter; }
    public List<Source> assemble(UUID kb,List<Hit> hits) {
        List<Source> result=new ArrayList<>(); Set<String> texts=new HashSet<>(); Map<UUID,Integer> perDoc=new HashMap<>(); int used=0;
        // Round-robin over documents: the second pass fills remaining slots.
        for(int pass=1;pass<=2;pass++) for(Hit h:hits) {
            if(!h.knowledgeBaseId().equals(kb) || h.score()<settings.minimumScore() || !filter.allowed(h.text())) continue;
            String normalized=h.text().toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");
            if(texts.contains(normalized) || perDoc.getOrDefault(h.documentId(),0)>=pass) continue;
            if(used+h.text().length()+80>settings.contextCharacters()) continue;
            // Near duplicates are skipped using token-set Jaccard, with no model call.
            boolean duplicate=result.stream().anyMatch(s->similar(s.hit().text(),h.text())>0.9);
            if(duplicate) continue;
            used+=h.text().length()+80; texts.add(normalized); perDoc.merge(h.documentId(),1,Integer::sum);
            result.add(new Source("S"+(result.size()+1),h));
        }
        return List.copyOf(result);
    }
    private double similar(String a,String b) {
        Set<String> x=new HashSet<>(List.of(a.toLowerCase(Locale.ROOT).split("\\W+")));
        Set<String> y=new HashSet<>(List.of(b.toLowerCase(Locale.ROOT).split("\\W+")));
        Set<String> union=new HashSet<>(x); union.addAll(y); x.retainAll(y);
        return union.isEmpty() ? 0 : (double)x.size()/union.size();
    }
}
