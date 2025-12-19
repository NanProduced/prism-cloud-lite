package nan.produced.prism.device.application.domain.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 终端指令领域对象
 * <p>也就是设备端API接口的定义</p>
 *
 * @author Nan
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeviceCommand {

    /**
     * 指令ID - UUID
     */
    private String commandId;

    /**
     * device-service内部适配设备的指令ID
     * <p>设备只支持Integer，保证一定时间内同一设备收到的指令Id唯一即可</p>
     */
    private Integer queueId;

    /**
     * 设备ID
     */
    private Long deviceId;

    /**
     * 指令操作类型 (用于去重判断)
     * <p>设备API URL</p>
     */
    private String authorUrl;

    /**
     * 指令内容JSON
     * <p>设备API Body参数</p>
     */
    private String contentRaw;

    /**
     * 终端执行方式
     * <p>0-GET, 1-POST, 2-PUT, 3-DELETE</p>
     * <p>设备API HTTP method</p>
     */
    private Integer karma;

    /**
     * 指令在 device-service 中的缓存 TTL（分钟）
     * <p>为空时由服务端使用默认值</p>
     */
    private Long ttlMinutes;

    /**
     * 指令被缓存的时间（约等于下发时间）
     */
    private LocalDateTime cacheTime;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 检查指令是否过期
     */
    public boolean isExpired() {
        return expireTime != null && LocalDateTime.now().isAfter(expireTime);
    }

}
