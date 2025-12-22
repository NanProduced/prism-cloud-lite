package nan.produced.prism.core.program.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "取消发布（Unpublish / Undeploy）请求")
public class ProgramUnpublishReq {

    @NotNull
    @Schema(description = "范围：SELECTED/RUNNING", requiredMode = Schema.RequiredMode.REQUIRED)
    private ProgramPublishScope scope;

    @Schema(description = "目标设备ID集合（scope=SELECTED 时使用；scope=RUNNING 时可不传）")
    private List<Long> deviceIds;
}

