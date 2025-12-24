package nan.produced.prism.core.media.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 移动媒体库节点请求
 */
@Data
@Schema(name = "MoveNodesRequest", description = "移动节点请求（文件夹/素材）")
public class MoveNodesRequest {

    @NotEmpty(message = "nodeIds is required")
    @Schema(description = "待移动的节点ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> nodeIds;

    /**
     * 目标父文件夹ID（null = 根目录）
     */
    @Schema(description = "目标父文件夹ID（不传/null 表示根目录）")
    private String targetParentId;
}
