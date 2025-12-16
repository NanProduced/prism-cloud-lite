package nan.produced.prism.core.device.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "创建/更新标签请求")
@Data
public class OperateTagReq {

    /**
     * 标签名称
     */
    @NotNull
    @Size(min = 1, max = 64)
    @Schema(description = "标签名称", example = "Lobby")
    private String tagName;

    /**
     * 颜色 - 后端不处理，由前端控制映射，后端仅存储颜色值
     */
    @NotNull
    @Schema(description = "颜色（preset key 或 hex）", example = "sky")
    private String color;

    /**
     * 图标 - 后端不处理，由前端控制映射，后端仅存储图标值
     */
    @NotNull
    @Schema(description = "图标（Lucide icon name）", example = "Building2")
    private String icon;

    /**
     * 描述
     */
    @Size(min = 1, max = 128)
    @Schema(description = "描述", example = "大厅入口屏")
    private String description;
}
