package nan.produced.prism.core.device.domain;

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
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "pcc_device")
public class DeviceEntity {

    /* ====== 业务字段 ====== */

    @Id
    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "device_name", nullable = false, length = 128)
    private String deviceName;

    @Column(name = "description", length = 256)
    private String description;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "online_status")
    private Integer onlineStatus;

    @Column(name = "onboarding_time")
    private OffsetDateTime onboardingTime;

    @Column(name = "last_report_time")
    private OffsetDateTime lastReportTime;

    @Column(name = "created_at")
    private OffsetDateTime createTime;

    /* ====== 设备属性冗余字段 ====== */

    @Column(name = "model", length = 128)
    private String model;

    @Column(name = "version", length = 64)
    private String version;

    @Column(name = "brightness")
    private Integer brightness;

    @Column(name = "network_type")
    private Integer networkType;

    @Column(name = "playing_program", length = 256)
    private String playingProgram;

    @Column(name = "resolution", length = 64)
    private String resolution;

    @Column(name = "total_storage", length = 64)
    private Long totalStorage;

    @Column(name = "free_storage", length = 64)
    private Long freeStorage;

    /**
     * 省电模式状态（0-休眠，1-唤醒）
     * <p>该状态与 onlineStatus 无关：设备可在线但处于休眠。</p>
     */
    @Column(name = "power_status")
    private Integer powerStatus;

    /* ====== 设备属性JSON字段 ====== */

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private DeviceProperties properties;

    public DeviceEntity(Long deviceId, DeviceProperties  properties) {
        this.deviceId = deviceId;
        this.properties = properties;
    }
}
