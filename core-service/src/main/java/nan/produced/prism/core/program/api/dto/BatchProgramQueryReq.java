package nan.produced.prism.core.program.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "按节目 ID 批量查询请求")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchProgramQueryReq {

    @NotEmpty
    @Schema(description = "节目 ID 列表（UUID）", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<@NotNull UUID> programIds;
}

