package nan.produced.prism.core.device.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Schema(description = "创建设备响应")
@Data
@AllArgsConstructor
public class CreateDeviceResp {

    @Schema(description = "设备ID", example = "10001")
    private Long deviceId;

    @Schema(description = "设备名称", example = "Lobby Screen A")
    private String deviceName;

    @Schema(description = "设备账号", example = "device_001")
    private String deviceAccount;

    @Schema(description = "设备密码", example = "********")
    private String devicePassword;

}
