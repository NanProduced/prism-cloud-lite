package nan.produced.prism.device.application.dto.record;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 登录时间更新记录
 * <p>用于异步批处理缓冲池</p>
 *
 * @author Nan
 */
@Data
@Builder
public class DeviceLoginRecord {

    /**
     * 设备ID
     */
    private Long deviceId;

    /**
     * 客户端IP
     */
    private String clientIp;

    /**
     * 更新时间
     */
    private OffsetDateTime updateTime;

    /**
     * 创建记录的时间戳（用于去重和统计）
     */
    private Long createTimestamp;

    /**
     * 创建登录更新记录
     */
    public static DeviceLoginRecord create(Long deviceId, String clientIp) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return DeviceLoginRecord.builder()
                .deviceId(deviceId)
                .clientIp(clientIp)
                .updateTime(now)
                .createTimestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建登录更新记录（指定时间）
     */
    public static DeviceLoginRecord create(Long deviceId, String clientIp, OffsetDateTime updateTime) {
        return DeviceLoginRecord.builder()
                .deviceId(deviceId)
                .clientIp(clientIp)
                .updateTime(updateTime)
                .createTimestamp(System.currentTimeMillis())
                .build();
    }

}
