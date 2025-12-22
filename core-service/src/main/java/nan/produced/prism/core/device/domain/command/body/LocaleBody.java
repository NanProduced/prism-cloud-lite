package nan.produced.prism.core.device.domain.command.body;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.command.DeviceActionBodyBase;

@EqualsAndHashCode(callSuper = true)
@Schema(description = "地区/语言设置参数")
@Data
public class LocaleBody extends DeviceActionBodyBase {

    @NotNull
    @Schema(description = "国家", example = "CN", requiredMode = Schema.RequiredMode.REQUIRED)
    private String country;

    @NotNull
    @Schema(description = "语言", example = "zh", requiredMode = Schema.RequiredMode.REQUIRED)
    private String language;
}
