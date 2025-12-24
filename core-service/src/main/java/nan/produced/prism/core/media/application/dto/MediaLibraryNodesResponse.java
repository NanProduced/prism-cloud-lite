package nan.produced.prism.core.media.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 媒体库节点列表响应
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "MediaLibraryNodesResponse", description = "素材库节点列表响应（cursor 分页）")
public class MediaLibraryNodesResponse {

    @Schema(description = "当前页节点列表（文件夹优先）")
    private List<MediaNodeDto> items;

    @Schema(description = "下一页游标（offset 字符串）；为 null 表示无下一页", example = "20")
    private String nextCursor;
}
