package nan.produced.prism.core.assistant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "assistant_chat_token_audit_log", schema = "assistant")
public class AssistantChatTokenAuditEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "provider", length = 64)
    private String provider;

    @Column(name = "input_summary", columnDefinition = "text")
    private String inputSummary;

    @Column(name = "output_summary", columnDefinition = "text")
    private String outputSummary;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
