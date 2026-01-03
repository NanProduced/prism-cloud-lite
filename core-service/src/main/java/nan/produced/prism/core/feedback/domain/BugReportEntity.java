package nan.produced.prism.core.feedback.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pcc_bug_report")
public class BugReportEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "user_public_id", nullable = false, length = 64)
    private String userPublicId;

    @Column(name = "user_email", nullable = false, length = 160)
    private String userEmail;

    @Column(name = "user_phone", length = 40)
    private String userPhone;

    @Column(name = "contact_email", length = 160)
    private String contactEmail;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content_html", nullable = false, columnDefinition = "text")
    private String contentHtml;

    @Column(name = "content_plain", nullable = false, columnDefinition = "text")
    private String contentPlain;

    @Column(name = "page_url", length = 500)
    private String pageUrl;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attachments", nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> attachments = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private BugReportStatus status = BugReportStatus.OPEN;

    @Column(name = "admin_note", columnDefinition = "text")
    private String adminNote;

    @Column(name = "reply_subject", length = 200)
    private String replySubject;

    @Column(name = "reply_html", columnDefinition = "text")
    private String replyHtml;

    @Column(name = "replied_at")
    private Instant repliedAt;

    @Column(name = "replied_by", length = 64)
    private String repliedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

