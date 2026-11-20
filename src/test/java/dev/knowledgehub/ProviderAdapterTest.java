package dev.knowledgehub;

import dev.knowledgehub.embedding.SpringAiEmbeddingProvider;
import dev.knowledgehub.generation.SpringAiGroundedChat;
import dev.knowledgehub.generation.ContextAssembler;
import dev.knowledgehub.retrieval.ChunkRetriever;
import dev.knowledgehub.shared.*;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.Prompt;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ProviderAdapterTest {
    @Test void embeddingUsesSpringAiAndNomicPrefixes() {
        var model=mock(EmbeddingModel.class); float[] vector=new float[768];vector[0]=1;
        when(model.embed(anyString())).thenReturn(vector);
        try(var executor=new ProviderExecutor(TestSettings.settings(),new SimpleMeterRegistry())) {
            var provider=new SpringAiEmbeddingProvider(model,TestSettings.settings(),executor,"nomic-embed-text:v1.5");
            assertThat(provider.embed("policy",false)).isEqualTo(vector);provider.embed("refund",true);
            verify(model).embed("search_document: policy");verify(model).embed("search_query: refund");
        }
    }
    @Test void groundedChatSeparatesPolicyEvidenceAndQuestionAndParsesStructuredDraft() {
        ChatModel model=mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("{\"status\":\"ANSWERED\",\"claims\":[{\"text\":\"Thirty days\",\"sourceIds\":[\"S1\"]}]}")))));
        UUID kb=UUID.randomUUID();var hit=new ChunkRetriever.Hit(UUID.randomUUID(),kb,UUID.randomUUID(),"a.md",1,0,"",UUID.randomUUID(),"Refunds: thirty days",1,1);
        try(var executor=new ProviderExecutor(TestSettings.settings(),new SimpleMeterRegistry())) {
            var chat=new SpringAiGroundedChat(model,executor,"local-test");
            var completion=chat.generate("What is the window?",List.of(new ContextAssembler.Source("S1",hit)));
            assertThat(completion.draft().claims().getFirst().sourceIds()).containsExactly("S1");
            ArgumentCaptor<Prompt> prompt=ArgumentCaptor.forClass(Prompt.class);verify(model).call(prompt.capture());
            var messages=prompt.getValue().getInstructions();assertThat(messages).hasSize(3);
            assertThat(messages.getFirst()).isInstanceOf(SystemMessage.class);
            assertThat(messages.getFirst().getText()).doesNotContain(hit.text());
            assertThat(messages.get(1)).isInstanceOf(UserMessage.class);assertThat(messages.get(1).getText()).contains(hit.text());
            assertThat(messages.getLast().getText()).contains("What is the window?");
        }
    }
    @Test void malformedOutputAndOutageAreDifferentErrors() {
        ChatModel model=mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("not JSON")))));
        try(var executor=new ProviderExecutor(TestSettings.settings(),new SimpleMeterRegistry())) {
            var chat=new SpringAiGroundedChat(model,executor,"local-test");
            assertThatThrownBy(()->chat.generate("query",List.of())).isInstanceOfSatisfying(HubException.class,e->assertThat(e.code()).isEqualTo("INVALID_MODEL_RESPONSE"));
            when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("provider unavailable"));
            assertThatThrownBy(()->chat.generate("query",List.of())).isInstanceOfSatisfying(HubException.class,e->assertThat(e.code()).isEqualTo("MODEL_UNAVAILABLE"));
        }
    }
}
