package nan.produced.prism.core.media.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * 媒体库节点（文件夹/素材）
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MediaNodeDto {

    private String id;

    private String name;

    private String parentId;

    private String createdAt;

    private String updatedAt;

    /**
     * folder | asset
     */
    private String type;

    /**
     * 文件夹：子节点数量（folder + asset）
     */
    private Integer childrenCount;

    /**
     * 素材：image | video | document | other
     */
    private String assetKind;

    private String mimeType;

    private Long sizeBytes;

    private String extension;

    private String coverUrl;

    private String assetUrl;

    private Integer width;

    private Integer height;

    private Long durationMs;
}

