package nan.produced.prism.core.program.domain;

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

/**
 * 节目草稿（编辑器工作区快照）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pc_program_draft")
public class ProgramDraftEntity {

    /**
     * 草稿 ID（UUID）
     */
    @Id
    @Column(name = "draft_id", nullable = false)
    private UUID draftId;

    @Column(name = "program_id", nullable = false)
    private UUID programId;

    /**
     * 草稿基线版本；0 表示 Blank，其他表示基于某个已发布版本继续编辑。
     */
    @Column(name = "base_version", nullable = false)
    private Integer baseVersion;

    /**
     * 编辑器保存的 VSN JSON（JSONB）。
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "vsn_json", nullable = false, columnDefinition = "jsonb")
    private String vsnJson;

    /**
     * 草稿封面截图（用于节目列表未发布场景的封面）。
     */
    @Column(name = "cover_object_key", length = 512)
    private String coverObjectKey;

    @Column(name = "cover_content_type", length = 128)
    private String coverContentType;

    @Column(name = "cover_size_bytes")
    private Long coverSizeBytes;

    /**
     * 可选：内容 hash，用于更准确的“未发布更改”判断。
     */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
