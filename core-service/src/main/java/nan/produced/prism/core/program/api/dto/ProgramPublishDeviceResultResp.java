package nan.produced.prism.core.program.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "发布到单设备的结果摘要")
public class ProgramPublishDeviceResultResp {

    @Schema(description = "设备ID")
    private Long deviceId;

    @Schema(description = "动作：deploy/update/rollback/no-change/skip")
    private String action;

    @Schema(description = "是否实际写入发布关系并触发下发")
    private boolean affected;

    @Schema(description = "下发指令 commandId（用于追踪/排错）")
    private String commandId;

    @Schema(description = "device-service 队列ID（若 accepted）")
    private Integer queuedId;

    @Schema(description = "是否被 device-service 接受")
    private boolean accepted;

    @Schema(description = "device-service 失败信息（若 rejected）")
    private String errorMessage;
}

