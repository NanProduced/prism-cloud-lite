package nan.produced.prism.core.media.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * 移动媒体库节点响应
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MoveNodesResponse {

    private int moved;
}

