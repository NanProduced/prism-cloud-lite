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
 * 设备素材播放区间事实数据（用于素材播放时长/次数的范围聚合与分桶聚合）。
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
        name = "pc_device_media_play_session",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_device_media_play_session_unique",
                        columnNames = {"device_id", "media_id", "start_at", "end_at"}
                )
        }
)
public class DeviceMediaPlaySessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    /**
     * 素材 ID（平台侧 MediaAsset.id）。
     * <p>按当前 VSN 构建逻辑：下发时将素材 ID 写入 item.originName，因此设备上报的 resOriginName 可直接作为 mediaId。</p>
     */
    @Column(name = "media_id", nullable = false, length = 64)
    private String mediaId;

    /**
     * 设备上报的 originName 原始值（通常等于 mediaId，用于排障/对账）。
     */
    @Column(name = "res_origin_name", length = 256)
    private String resOriginName;

    @Column(name = "res_md5_name", length = 256)
    private String resMd5Name;

    @Column(name = "item_type", length = 64)
    private String itemType;

    /**
     * 是否 LAN 节目来源（无法与平台节目版本建立关联时置为 true）。
     */
    @Column(name = "is_lan", nullable = false)
    private boolean lan;

    /**
     * 平台节目 ID（可为空：LAN 节目或解析失败）。
     */
    @Column(name = "program_id")
    private UUID programId;

    /**
     * 平台节目版本号（Release.version，可为空）。
     */
    @Column(name = "release_version")
    private Integer releaseVersion;

    @Column(name = "program_vsn", length = 320)
    private String programVsn;

    @Column(name = "program_name_snapshot", length = 256)
    private String programNameSnapshot;

    @Column(name = "vsn_md5", length = 64)
    private String vsnMd5;

    @Column(name = "vsn_size_bytes")
    private Long vsnSizeBytes;

    @Column(name = "page_name", length = 128)
    private String pageName;

    @Column(name = "page_index")
    private Integer pageIndex;

    @Column(name = "region_name", length = 128)
    private String regionName;

    @Column(name = "region_index")
    private Integer regionIndex;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;

    /**
     * 设备上报的“实际播放时长”（单位以设备上报为准），用于对账/排障，可为空。
     */
    @Column(name = "reported_duration")
    private Long reportedDuration;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
