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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "assistant_tool_audit_log", schema = "assistant")
public class AssistantToolCallAuditEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "tool_call_id", length = 64)
    private String toolCallId;

    @Column(name = "tool_name", nullable = false, length = 128)
    private String toolName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_json", columnDefinition = "jsonb")
    private String inputJson;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "input_summary", columnDefinition = "text")
    private String inputSummary;

    @Column(name = "output_summary", columnDefinition = "text")
    private String outputSummary;

    @Column(name = "success", nullable = false)
    private Boolean success;

    @Column(name = "elapsed_ms", nullable = false)
    private Long elapsedMs;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "conversation_id")
    private UUID conversationId;

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

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
