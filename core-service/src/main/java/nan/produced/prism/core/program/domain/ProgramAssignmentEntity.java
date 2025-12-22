package nan.produced.prism.core.program.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 节目期望下发关系（设备 ⇄ 节目版本/Release）的绑定记录（由平台主动操作产生）。
 *
 * <p>区别：</p>
 * <ul>
 *   <li>assignment：用户操作（发布/排程绑定）产生的“期望状态”（允许设备下载）</li>
 *   <li>deployment：设备上报产生的“事实状态”（设备实际下载/已下载）</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pc_program_assignment")
@IdClass(ProgramAssignmentId.class)
public class ProgramAssignmentEntity {

    @Id
    @Column(name = "program_id", nullable = false)
    private UUID programId;

    @Id
    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "release_version", nullable = false)
    private Integer releaseVersion;

    @Column(name = "release_program_id", nullable = false)
    private Integer releaseProgramId;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}

