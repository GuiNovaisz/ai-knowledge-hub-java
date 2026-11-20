package dev.knowledgehub.shared;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("hub")
public record HubSettings(@Min(768) @Max(768) int dimensions, @NotBlank String embeddingVersion,
        @Min(200) @Max(2000) int chunkCharacters, @Min(0) @Max(199) int chunkOverlap,
        @Min(1000) @Max(1000000) int maxCharacters, @Min(1) @Max(200) int maxPages,
        @Min(1) @Max(500) int maxChunks, @Min(200) @Max(16000) int contextCharacters,
        @DecimalMin("0.0") @DecimalMax("1.0") double minimumScore,
        @Min(1) @Max(120) int providerTimeoutSeconds) {}
