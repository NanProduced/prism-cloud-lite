package nan.produced.prism.core.media.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 媒体库节点（文件夹/素材）
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "MediaNodeDto", description = "素材库节点（文件夹或素材）")
public class MediaNodeDto {

    @Schema(description = "节点ID（文件夹ID或素材ID）")
    private String id;

    @Schema(description = "名称：文件夹为 folder name；素材为 title")
    private String name;

    @Schema(description = "父文件夹ID（根目录为 null）")
    private String parentId;

    @Schema(description = "创建时间（ISO-8601）")
    private String createdAt;

    @Schema(description = "更新时间（ISO-8601）")
    private String updatedAt;

    /**
     * folder | asset
     */
    @Schema(description = "节点类型：folder|asset", allowableValues = { "folder", "asset" })
    private String type;

    /**
     * 文件夹：子节点数量（folder + asset）
     */
    @Schema(description = "文件夹子节点数量（folder + asset）；仅 folder 有值")
    private Integer childrenCount;

    /**
     * 素材：image | video | document | other
     */
    @Schema(description = "素材类型：image|video|document|other；仅 asset 有值")
    private String assetKind;

    @Schema(description = "素材 MIME 类型；仅 asset 有值")
    private String mimeType;

    @Schema(description = "素材大小（字节）；仅 asset 有值")
    private Long sizeBytes;

    @Schema(description = "文件扩展名；仅 asset 有值", example = "mp4")
    private String extension;

    @Schema(description = "封面 URL（可选）；仅 asset 有值")
    private String coverUrl;

    @Schema(description = "素材访问 URL（可选，按配置生成）；仅 asset 有值")
    private String assetUrl;

    @Schema(description = "宽度（图片/视频，可选）")
    private Integer width;

    @Schema(description = "高度（图片/视频，可选）")
    private Integer height;

    @Schema(description = "时长（视频，可选，毫秒）")
    private Long durationMs;
}
