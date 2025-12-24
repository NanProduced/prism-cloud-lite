package nan.produced.prism.core.media.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 媒体库使用量统计响应
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "MediaLibraryUsageResponse", description = "素材库使用量统计响应")
public class MediaLibraryUsageResponse {

    @Schema(description = "配额上限（字节）")
    private long quotaBytes;

    @Schema(description = "已使用（字节）")
    private long usedBytes;

    @Schema(description = "按素材类型统计的字节数（key=image|video|document|other）")
    private Map<String, Long> bytesByKind;

    @Schema(description = "计数统计（key=image|video|document|other|folders）")
    private Map<String, Long> counts;
}
