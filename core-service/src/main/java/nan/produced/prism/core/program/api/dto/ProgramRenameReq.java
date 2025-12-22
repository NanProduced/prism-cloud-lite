package nan.produced.prism.core.program.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "节目重命名请求")
public class ProgramRenameReq {

    @NotBlank
    @Schema(description = "新名称", example = "New Program Name", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
}

