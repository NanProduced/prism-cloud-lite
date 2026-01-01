package nan.produced.prism.core.assistant.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "assistant.rag")
public record AssistantRagProperties(
        @Valid @NotNull Embedding embedding,
        @Valid @NotNull Ingest ingest,
        @Valid @NotNull Chunk chunk
) {

    public record Embedding(
            @NotBlank String baseUrl,
            @NotBlank String model,
            @Min(1) @Max(8192) int dimension,
            @Min(1) @Max(256) int batchSize
    ) {
    }

    public record Ingest(
            boolean enabled,
            boolean exitAfterRun,
            @NotBlank String docVersion,
            @NotEmpty List<@NotBlank String> helpRoots,
            @NotBlank String requireAudience,
            @NotBlank String requireStatus,
            @Min(0) int maxDocs
    ) {
    }

    public record Chunk(
            @Min(256) @Max(20000) int maxChars,
            @Min(0) @Max(5000) int overlapChars
    ) {
    }
}

