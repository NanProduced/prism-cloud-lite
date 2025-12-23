package nan.produced.prism.core.device.domain.report.log;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 设备日志 （终端设备上报的日志）
 *
 * @author Nan
 */
@Data
@Entity
@Table(name = "pc_device_log")
public class DeviceLogEntity {

    private Long id;

    private Long deviceId;

    private String description;

    private Integer operation;

    private String logArg1;

    private String logArg2;

    private String logArg3;

    private String logArg4;

    private String logArg5;

    private String logArg6;

    /**
     * 上报时间 - 设备本地时区
     */
    private OffsetDateTime reportTime;

    /**
     * 创建时区 - 服务器时区（UTC）
     */
    private OffsetDateTime createTime;
}
