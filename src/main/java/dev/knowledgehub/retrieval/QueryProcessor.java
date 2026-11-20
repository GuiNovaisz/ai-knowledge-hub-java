package dev.knowledgehub.retrieval;

import java.text.Normalizer;
import java.util.*;

/** Bounded lexical candidate expansion; no LLM, SQL interpolation or implicit synonyms. */
public final class QueryProcessor {
    private static final Set<String> STOPWORDS=Set.of("what","is","the","how","many","long","do","does","i","have","to","a","an","and","of","in","for","it","my","me","are","can","which","o","aos","as","os","de","da","dois","em","que","qual","como","quanto","por","para","um","uma");
    private QueryProcessor() {}
    public static String normalize(String query) {
        return Normalizer.normalize(query,Normalizer.Form.NFKC).replaceAll("\\s+"," ").strip();
    }
    public static String lexical(String query) {
        return Arrays.stream(normalize(query).toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}_-]+"))
            .filter(t->!t.isBlank() && !STOPWORDS.contains(t)).distinct().limit(24)
            .reduce((a,b)->a+" OR "+b).orElse("");
    }
}
