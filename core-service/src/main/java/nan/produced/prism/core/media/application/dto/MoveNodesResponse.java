package nan.produced.prism.core.media.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 移动媒体库节点响应
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "MoveNodesResponse", description = "移动节点响应")
public class MoveNodesResponse {

    @Schema(description = "成功移动的节点数量", example = "3")
    private int moved;
}
