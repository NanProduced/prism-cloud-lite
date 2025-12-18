package nan.produced.prism.core.media.application.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 移动媒体库节点请求
 */
@Data
public class MoveNodesRequest {

    @NotEmpty(message = "nodeIds is required")
    private List<String> nodeIds;

    /**
     * 目标父文件夹ID（null = 根目录）
     */
    private String targetParentId;
}

