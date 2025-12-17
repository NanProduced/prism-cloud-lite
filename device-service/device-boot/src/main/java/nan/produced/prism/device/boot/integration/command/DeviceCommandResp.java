package nan.produced.prism.device.boot.integration.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量下发指令响应
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

        /**
         * 是否覆盖
         * <p>当某设备队列中有未执行的同一authorUrl的指令，新下发的会覆盖旧的</p>
         */
        private boolean covered;

        private String sendMethod;

        /**
         * device-service内部适配设备的指令ID
         * <p>设备只支持Integer，保证一定时间内同一设备收到的指令Id唯一即可</p>
         */
        private Integer queuedId;

        private String errorMessage;
    }
}

