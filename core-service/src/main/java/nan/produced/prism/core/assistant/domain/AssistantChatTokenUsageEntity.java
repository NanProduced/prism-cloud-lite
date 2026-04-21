package nan.produced.prism.core.assistant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "assistant_chat_token_usage_daily", schema = "assistant")
@IdClass(AssistantChatTokenUsageId.class)
public class AssistantChatTokenUsageEntity {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Id
    @Column(name = "day", nullable = false, updatable = false)
    private LocalDate day;

    @Column(name = "used_tokens", nullable = false)
    @Builder.Default
    private Long usedTokens = 0L;

    @Column(name = "frozen_tokens", nullable = false)
    @Builder.Default
    private Long frozenTokens = 0L;

    @Column(name = "last_frozen_at")
    private Instant lastFrozenAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
