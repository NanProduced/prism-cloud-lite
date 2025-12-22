package nan.produced.prism.core.program.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建节目请求")
public class CreateProgramReq {

    @NotBlank
    @Schema(description = "节目名称", example = "My Program", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotNull
    @Schema(description = "画布宽度", example = "1920", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer width;

    @NotNull
    @Schema(description = "画布高度", example = "1080", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer height;
}

