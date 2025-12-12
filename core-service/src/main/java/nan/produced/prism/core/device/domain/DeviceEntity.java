package nan.produced.prism.core.device.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@Entity
@Table(name = "device")
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
    private LocalDateTime onboardingTime;

    @Column(name = "last_report_time")
    private LocalDateTime lastReportTime;

    @Column(name = "created_at")
    private LocalDateTime createTime;

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
    private String totalStorage;

    @Column(name = "free_storage", length = 64)
    private String freeStorage;

    /* ====== 设备属性JSON字段 ====== */

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "properties", columnDefinition = "jsonb")
    private DeviceProperties properties;
}
