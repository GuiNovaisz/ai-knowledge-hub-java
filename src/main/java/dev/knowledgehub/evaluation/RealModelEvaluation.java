package dev.knowledgehub.evaluation;

import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Manual/opt-in only; never introduces an Ollama requirement into CI. */
@Component
@Profile("evaluation")
public class RealModelEvaluation implements ApplicationRunner {
    public record Golden(String id, String query, String knowledgeBase, List<String> relevantDocuments,
        List<String> relevantChunks, String expectedAnswerability, List<String> expectedCitations) {}

    private final RealModelRecorder recorder;

    public RealModelEvaluation(RealModelRecorder recorder) { this.recorder = recorder; }

    @Override
    public void run(ApplicationArguments args) throws Exception { recorder.record(); }
}
