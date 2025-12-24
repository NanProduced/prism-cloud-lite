package nan.produced.prism.core.telemetry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备节目播放区间事实数据（用于节目播放时长/次数的范围聚合与分桶聚合）。
 *
 * <p>说明：</p>
 * <ul>
 *   <li>所有时间统一存储为 UTC（TIMESTAMPTZ）</li>
 *   <li>range 列 period 为数据库生成列，用于 GiST overlap 过滤（不在实体中映射）</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "pcc_device_program_play_session",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_device_program_play_session_platform",
                        columnNames = {"device_id", "program_id", "release_version", "start_at", "end_at"}
                ),
                @UniqueConstraint(
                        name = "uk_device_program_play_session_lan",
                        columnNames = {"device_id", "lan_program_id", "start_at", "end_at"}
                )
        }
)
public class DeviceProgramPlaySessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    /**
     * 是否 LAN 节目（设备本地生成/局域网下发）。
     */
    @Column(name = "is_lan", nullable = false)
    private boolean lan;

    /**
     * LAN 节目 ID（设备生成的 UUID 字符串）；非 LAN 为空。
     */
    @Column(name = "lan_program_id", length = 64)
    private String lanProgramId;

    /**
     * 平台节目 ID；LAN 节目为空。
     */
    @Column(name = "program_id")
    private UUID programId;

    /**
     * 平台节目版本号（Release.version）；LAN 节目为空。
     */
    @Column(name = "release_version")
    private Integer releaseVersion;

    /**
     * 设备上报的节目 VSN 文件名（用于排障/对账，可为空）。
     */
    @Column(name = "program_vsn", length = 320)
    private String programVsn;

    /**
     * 设备上报的节目名称（用于排障/对账，可为空）。
     */
    @Column(name = "program_name_snapshot", length = 256)
    private String programNameSnapshot;

    /**
     * 可选：由 VSN 文件名解析得到，用于回填 programId/releaseVersion 或用于排障。
     */
    @Column(name = "vsn_md5", length = 64)
    private String vsnMd5;

    @Column(name = "vsn_size_bytes")
    private Long vsnSizeBytes;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
