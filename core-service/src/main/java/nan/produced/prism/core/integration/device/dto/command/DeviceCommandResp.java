package nan.produced.prism.core.integration.device.dto.command;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * device-service `/internal/device/command` 响应 data 字段
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCommandResp {

    private int total;

    private int accepted;

    private List<CommandResult> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommandResult {

        private Long deviceId;

        private String commandId;

        private boolean accepted;

        private boolean covered;

        private String sendMethod;

        private Integer queuedId;

        private String errorMessage;
    }
}

