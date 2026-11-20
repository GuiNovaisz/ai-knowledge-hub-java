package dev.knowledgehub.embedding;

import dev.knowledgehub.shared.*;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SpringAiEmbeddingProvider implements EmbeddingProvider {
    private final EmbeddingModel model;
    private final HubSettings settings;
    private final ProviderExecutor executor;
    private final String modelName;
    public SpringAiEmbeddingProvider(EmbeddingModel model,HubSettings settings,ProviderExecutor executor,
        @Value("${spring.ai.ollama.embedding.options.model}") String modelName) {
        this.model=model; this.settings=settings; this.executor=executor; this.modelName=modelName;
    }
    public float[] embed(String text,boolean query) {
        float[] vector=executor.call("EMBEDDING",()->model.embed((query ? "search_query: " : "search_document: ")+text));
        validate(vector,settings.dimensions()); return vector;
    }
    public static void validate(float[] vector,int dimensions) {
        if(vector==null || vector.length!=dimensions) throw new HubException("EMBEDDING_DIMENSION_MISMATCH",502);
        double norm=0;
        for(float v:vector) { if(!Float.isFinite(v)) throw new HubException("INVALID_EMBEDDING",502); norm+=v*v; }
        if(norm==0) throw new HubException("INVALID_EMBEDDING",502);
    }
    public String provider() { return "ollama"; }
    public String model() { return modelName; }
}
