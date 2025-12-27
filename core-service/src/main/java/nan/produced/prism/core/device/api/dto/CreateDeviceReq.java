package nan.produced.prism.core.device.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "创建设备请求")
@Data
public class CreateDeviceReq {

    @Size(min = 1, max = 64)
    @NotNull
    @Schema(description = "设备显示名称", example = "Lobby Screen A")
    private String displayName;

    @Size(min = 8, max = 64)
    @NotNull
    @Schema(description = "设备账号（用于对接 device-service）", example = "device_001")
    private String account;

    @Size(min = 12, max = 64)
    @NotNull
    @Schema(description = "设备密码（用于对接 device-service）", example = "********")
    private String password;

    @Size(max = 128)
    @Schema(description = "设备描述", example = "大厅入口屏幕")
    private String description;
}
