package nan.produced.prism.core.device.domain.report.log;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 设备日志 （终端设备上报的日志）
 *
 * @author Nan
 */
@Data
@Entity
@Table(name = "pcc_device_log")
public class DeviceLogEntity {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "operation_id", nullable = false)
    private Integer operationId;

    @Column(name = "level", nullable = false)
    private Integer level;

    @Column(name = "log_type", length = 64)
    private String logType;

    @Column(name = "log_subtype1", length = 64)
    private String subtype1;

    @Column(name = "log_subtype2", length = 64)
    private String subtype2;

    @Column(name = "log_subtype3", length = 64)
    private String subtype3;

    @Column(name = "categories", length = 64)
    private String categories;

    @Column(name = "description")
    private String description;

    @Column(name = "device_time_raw", length = 64)
    private String deviceTimeRaw;

    @Column(name = "hand_status")
    private Integer handleStatus;

    @Column(name = "hand_time_raw", length = 64)
    private String handleTimeRaw;

    @Column(name = "log_arg1")
    private String logArg1;

    @Column(name = "log_arg2")
    private String logArg2;

    @Column(name = "log_arg3")
    private String logArg3;

    @Column(name = "log_arg4")
    private String logArg4;

    @Column(name = "log_arg5")
    private String logArg5;

    @Column(name = "log_arg6")
    private String logArg6;

    @Column(name = "others")
    private String others;

    /**
     * 上报时间 - 设备本地时区
     */
    @Column(name = "report_time")
    private OffsetDateTime reportTime;

    /**
     * 创建时区 - 服务器时区（UTC）
     */
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createTime;
}
