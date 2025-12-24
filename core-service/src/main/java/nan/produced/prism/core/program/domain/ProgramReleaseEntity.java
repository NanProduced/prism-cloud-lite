package nan.produced.prism.core.program.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
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
 * 节目发布版本（Release）。
 *
 * <p>平台侧：以 (programId, version) 组织版本链；设备侧：每个 Release 对应一个独立的 programId(Integer)。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pcc_program_release")
public class ProgramReleaseEntity {

    /**
     * 设备侧节目 ID（Colorlight ProgramId，Integer），每个 Release 唯一。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pcc_program_device_program_id_seq")
    @SequenceGenerator(
            name = "pcc_program_device_program_id_seq",
            sequenceName = "pcc_program_device_program_id_seq",
            allocationSize = 1)
    @Column(name = "device_program_id", nullable = false)
    private Integer deviceProgramId;

    @Column(name = "program_id", nullable = false)
    private UUID programId;

    /**
     * 平台侧版本号（从 1 递增）。
     */
    @Column(name = "version", nullable = false)
    private Integer version;

    /**
     * 设备侧展示名快照（建议格式：{Program.name}-v{version}）。
     *
     * <p>该字段在发布时固化，避免节目重命名导致设备侧标题/文件前缀变化，从而引发重复下载或对账困难。</p>
     */
    @Column(name = "device_title_snapshot", nullable = false, length = 160)
    private String deviceTitleSnapshot;

    @Column(name = "source_draft_id")
    private UUID sourceDraftId;

    /**
     * 发布时冻结的 VSN JSON（用于回显/导出；JSONB）。
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "vsn_json", nullable = false, columnDefinition = "jsonb")
    private String vsnJson;

    /**
     * 发布生成的 vsn 文件在对象存储中的 key（可为空：若选择动态生成/下载代理）。
     */
    @Column(name = "vsn_object_key")
    private String vsnObjectKey;

    @Column(name = "vsn_md5", nullable = false, length = 64)
    private String vsnMd5;

    @Column(name = "vsn_size_bytes", nullable = false)
    private Long vsnSizeBytes;

    /**
     * 发布版本封面截图（节目列表默认展示最新版本封面）。
     */
    @Column(name = "cover_object_key", length = 512)
    private String coverObjectKey;

    @Column(name = "cover_content_type", length = 128)
    private String coverContentType;

    @Column(name = "cover_size_bytes")
    private Long coverSizeBytes;

    /**
     * 设备下载所需文件清单快照（JSONB）。
     *
     * <p>至少包含 vsn 文件与该节目引用的全部素材（md5/size/s3Key 等）。</p>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "manifest_json", nullable = false, columnDefinition = "jsonb")
    private String manifestJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
