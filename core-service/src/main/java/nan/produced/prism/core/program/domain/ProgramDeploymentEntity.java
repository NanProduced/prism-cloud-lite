package nan.produced.prism.core.program.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 节目发布关系（设备 ⇄ 节目版本/Release）的长期绑定记录。
 *
 * <p>设备侧不理解“版本”，因此对设备暴露的 programId 为 release 的 deviceProgramId。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pcc_program_deployment")
@IdClass(ProgramDeploymentId.class)
public class ProgramDeploymentEntity {

    @Id
    @Column(name = "program_id", nullable = false)
    private UUID programId;

    @Id
    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * 平台侧版本号（用于 UI：deploy/update/rollback/no-change）。
     */
    @Column(name = "release_version", nullable = false)
    private Integer releaseVersion;

    /**
     * 设备侧节目 ID（Colorlight /wp-json/wp/v2/programs 返回的 id）。
     */
    @Column(name = "release_program_id", nullable = false)
    private Integer releaseProgramId;

    /**
     * 该设备视角的“发布时刻”，用于 /wp-json/wp/v2/programs 的 modified 字段。
     */
    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ProgramDeploymentStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
