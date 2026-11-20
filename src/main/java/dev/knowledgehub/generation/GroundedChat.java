package dev.knowledgehub.generation;

import java.util.List;
public interface GroundedChat {
    record Claim(String text,List<String> sourceIds) {}
    record Draft(String status,List<Claim> claims) {}
    record Completion(Draft draft,String model,Integer totalTokens) {}
    Completion generate(String question,List<ContextAssembler.Source> sources);
}
