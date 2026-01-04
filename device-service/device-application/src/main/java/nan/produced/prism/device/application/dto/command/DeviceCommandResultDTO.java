package nan.produced.prism.device.application.dto.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCommandResultDTO {

    private Long deviceId;

    private String commandId;

    private boolean accepted;

    /**
     * 是否覆盖
     * <p>当某设备队列中有未执行的同一authorUrl的指令，新下发的会覆盖旧的</p>
     */
    private boolean covered;

    /**
     * 下发指令方式
     * <p>Websocket/Cache</p>
     */
    private String sendMethod;

    /**
     * device-service内部适配设备的指令ID
     * <p>设备只支持Integer，保证一定时间内同一设备收到的指令Id唯一即可</p>
     */
    private Integer queuedId;

    private String errorMessage;

    public static DeviceCommandResultDTO failed(Long deviceId, String commandId, String errorMessage) {
        return DeviceCommandResultDTO.builder()
                .deviceId(deviceId)
                .commandId(commandId)
                .accepted(false)
                .errorMessage(errorMessage)
                .build();
    }

    public static DeviceCommandResultDTO success(Long deviceId, String commandId, String sendMethod, Integer queueId, boolean covered) {
        return DeviceCommandResultDTO.builder()
                .deviceId(deviceId)
                .commandId(commandId)
                .accepted(true)
                .covered(covered)
                .sendMethod(sendMethod)
                .queuedId(queueId)
                .build();
    }
}
