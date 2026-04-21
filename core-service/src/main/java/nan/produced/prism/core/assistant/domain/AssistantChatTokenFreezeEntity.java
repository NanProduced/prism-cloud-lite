package nan.produced.prism.core.assistant.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "assistant_chat_token_freeze", schema = "assistant")
public class AssistantChatTokenFreezeEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SETTLED = "SETTLED";
    public static final String STATUS_RELEASED = "RELEASED";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "day", nullable = false, updatable = false)
    private java.time.LocalDate day;

    @Column(name = "req_id", nullable = false, updatable = false, unique = true)
    private UUID reqId;

    @Column(name = "frozen_tokens", nullable = false)
    private Long frozenTokens;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = STATUS_ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
