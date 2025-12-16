package nan.produced.prism.core.device.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "设备标签（用于前端展示）")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TagVO {

    @Schema(description = "标签名称", example = "Lobby")
    private String tagName;

    @Schema(description = "标签唯一标识（slug）", example = "lobby")
    private String tagSlug;

    @Schema(description = "标签描述", example = "大厅入口屏")
    private String description;

    @Schema(description = "颜色（preset key 或 hex）", example = "sky")
    private String color;

    @Schema(description = "图标（Lucide icon name）", example = "Building2")
    private String icon;


}
