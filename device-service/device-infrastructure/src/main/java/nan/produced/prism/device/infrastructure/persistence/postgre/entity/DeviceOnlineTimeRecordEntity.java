package nan.produced.prism.device.infrastructure.persistence.postgre.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备在线时长记录
 *
 * @author Nan
 */
@Data
@Entity
@Table(name = "pcd_device_online_time_record")
public class DeviceOnlineTimeRecordEntity {

    /**
     * 主键
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    /**
     * 设备Id
     */
    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    /**
     * 上线时间
     */
    @Column(name = "online_time", nullable = false)
    private LocalDateTime onlineTime;

    /**
     * 下线时间
     */
    @Column(name = "offline_time", nullable = false)
    private LocalDateTime offlineTime;

    /**
     * 在线时长（s）
     */
    @Column(name = "duration", nullable = false)
    private Long duration;

    /**
     * 创建时间
     */
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @PrePersist
    void prePersist() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}
