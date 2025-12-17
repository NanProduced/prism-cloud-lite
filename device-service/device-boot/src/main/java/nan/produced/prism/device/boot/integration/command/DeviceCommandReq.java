package nan.produced.prism.device.boot.integration.command;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设备指令请求参数
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeviceCommandReq {

    /**
     * 设备ID
     */
    @NotNull
    private Long deviceId;

    /**
     * 指令ID - UUID
     */
    @NotNull
    private String commandId;

    /* 以下是指令内容，需符合指令内容格式要求 */

    @NotNull
    private String authorUrl;

    @NotNull
    private Integer karma;

    private Content content;

    /**
     * 指令在 device-service 中的缓存 TTL（分钟）
     * <p>为空时由服务端使用默认值</p>
     */
    private Long ttlMinutes;

    @Data
    public static class Content {

        private String raw;

    }
}
