package dev.knowledgehub.shared;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.*;
import org.springframework.context.annotation.*;

@Configuration
public class ApiDocumentation {
    @Bean OpenAPI openAPI() {
        return new OpenAPI().info(new Info().title("AI Knowledge Hub").version("0.1.0-dev")
            .description("Local-only grounded RAG. No authentication. Always supply knowledgeBaseId. Upload is synchronous; inspect document status for failures.")
            .license(new License().name("MIT")));
    }
}
