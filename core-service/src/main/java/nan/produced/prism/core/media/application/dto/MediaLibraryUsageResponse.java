package nan.produced.prism.core.media.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 媒体库使用量统计响应
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MediaLibraryUsageResponse {

    private long quotaBytes;

    private long usedBytes;

    private Map<String, Long> bytesByKind;

    private Map<String, Long> counts;
}

