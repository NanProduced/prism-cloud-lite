package nan.produced.prism.device.infrastructure.persistence.postgre.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备重新连接记录
 *
 * @author Nan
 */
@Data
@Entity
@Table(name = "device_reconnect_record")
public class DeviceReconnectRecordEntity {

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
     * 开始在线的时间
     */
    @Column(name = "start_online_time", nullable = false)
    private LocalDateTime startOnlineTime;

    /**
     * 掉线前最后上报时间
     */
    @Column(name = "last_report_time", nullable = false)
    private LocalDateTime lastReportTime;

    /**
     * 重连时间
     */
    @Column(name = "reconnect_time", nullable = false)
    private LocalDateTime reconnectTime;

    /**
     * 重连IP
     */
    @Column(name = "reconnect_ip", nullable = false)
    private String reconnectIp;

    /**
     * 重连方式
     */
    @Column(name = "reconnect_source", nullable = false)
    private String reconnectSource;

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
