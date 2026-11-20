package dev.knowledgehub.embedding;
public interface EmbeddingProvider {
    float[] embed(String text,boolean query);
    String provider();
    String model();
}
