package nan.produced.prism.core.device.domain.command;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import nan.produced.prism.core.device.domain.command.action.BrightnessAction;

@Schema(
        description = "设备动作（单入口多动作）",
        discriminatorProperty = "type",
        oneOf = {BrightnessAction.class},
        discriminatorMapping = {
                @DiscriminatorMapping(value = "BRIGHTNESS", schema = BrightnessAction.class)
        }
)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = BrightnessAction.class, name = "BRIGHTNESS")
})
@Data
public abstract class DeviceActionBase {

    @NotNull
    @Schema(description = "动作类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "BRIGHTNESS")
    private DeviceActionType type;

    @Schema(description = "指令在 device-service 中的缓存 TTL（分钟）；为空时由服务端使用默认值", example = "60")
    private Long ttlMinutes;

    @Schema(description = "客户端请求幂等键（可选，用于避免重复点击）", example = "req_20251219_0001")
    private String clientRequestId;

    @Valid
    @Schema(description = "动作参数（不同 type 的 body 结构不同）", requiredMode = Schema.RequiredMode.REQUIRED)
    public abstract DeviceActionBodyBase getBody();
}
