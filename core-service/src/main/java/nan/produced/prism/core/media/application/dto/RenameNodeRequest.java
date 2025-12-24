package nan.produced.prism.core.media.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 重命名媒体库节点请求
 */
@Data
@Schema(name = "RenameNodeRequest", description = "重命名节点请求（文件夹/素材）")
public class RenameNodeRequest {

    @NotBlank(message = "name is required")
    @Schema(description = "新名称（文件夹名或素材标题）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
}
