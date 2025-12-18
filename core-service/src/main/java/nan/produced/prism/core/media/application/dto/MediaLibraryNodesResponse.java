package nan.produced.prism.core.media.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 媒体库节点列表响应
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MediaLibraryNodesResponse {

    private List<MediaNodeDto> items;

    private String nextCursor;
}

