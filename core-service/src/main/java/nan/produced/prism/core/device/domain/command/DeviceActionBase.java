package nan.produced.prism.core.device.domain.command;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
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
}
