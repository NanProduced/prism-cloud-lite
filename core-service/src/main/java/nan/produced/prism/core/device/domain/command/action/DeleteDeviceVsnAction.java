package nan.produced.prism.core.device.domain.command.action;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.command.DeviceActionBase;
import nan.produced.prism.core.device.domain.command.DeviceActionType;
import nan.produced.prism.core.device.domain.command.body.DeleteDeviceVsnBody;

@Schema(description = "删除设备上的指定节目动作（按 source + vsnName）")
@Data
@EqualsAndHashCode(callSuper = true)
public class DeleteDeviceVsnAction extends DeviceActionBase {

    @NotBlank
    @Schema(description = "节目来源（device.properties.info.playing.source）", example = "internet")
    private String source;

    @NotBlank
    @Schema(description = "VSN 文件名（device.properties.info.playing.name）", example = "Playlist9017_783596d9ee396d7a604dac56a6979546_1332.vsn")
    private String vsnName;

    @NotNull
    @Schema(description = "删除动作 body（设备端要求固定为 {\"command\":\"\"}）")
    private DeleteDeviceVsnBody body;

    public DeleteDeviceVsnAction() {
        setType(DeviceActionType.DELETE_DEVICE_VSN);
        this.body = new DeleteDeviceVsnBody();
    }
}

