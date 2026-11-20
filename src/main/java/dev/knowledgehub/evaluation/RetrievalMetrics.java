package dev.knowledgehub.evaluation;

import java.util.*;

public final class RetrievalMetrics {
    private RetrievalMetrics() {}
    public record Score(double recall,double precision,double reciprocalRank,double hitRate) {}
    /** Document-level metrics, deduplicated by first occurrence. Unanswerable cases are excluded. */
    public static Score score(List<String> retrieved,Set<String> relevant,int k) {
        if(k<1 || relevant.isEmpty()) throw new IllegalArgumentException("Answerable case and positive K required");
        List<String> unique=retrieved.stream().distinct().limit(k).toList();
        int correct=0; double rr=0;
        for(int i=0;i<unique.size();i++) if(relevant.contains(unique.get(i))) { correct++; if(rr==0) rr=1.0/(i+1); }
        return new Score((double)correct/relevant.size(),(double)correct/k,rr,correct>0 ? 1 : 0);
    }
}
