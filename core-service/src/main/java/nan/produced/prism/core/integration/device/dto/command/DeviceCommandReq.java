package nan.produced.prism.core.integration.device.dto.command;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 调用 device-service `/internal/device/command` 的请求体（与 device-service 保持同字段名）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCommandReq {

    @NotNull
    private Long deviceId;

    @NotNull
    private String commandId;

    @NotNull
    private String authorUrl;

    @NotNull
    private Integer karma;

    private Content content;

    private Long ttlMinutes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Content {

        private String raw;
    }
}

