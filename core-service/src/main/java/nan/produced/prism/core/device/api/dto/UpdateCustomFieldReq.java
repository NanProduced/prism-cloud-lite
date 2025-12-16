package nan.produced.prism.core.device.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Schema(description = "更新设备自定义列请求（允许部分字段更新）")
@Data
public class UpdateCustomFieldReq {

    @Size(max = 128)
    @Schema(description = "显示名称", example = "City")
    private String displayName;

    @Size(max = 512)
    @Schema(description = "描述", example = "设备所在城市")
    private String description;

    @Size(max = 64)
    @Schema(description = "图标（Lucide icon name）", example = "MapPin")
    private String icon;

    @Schema(description = "是否为 Pro-only 列", example = "false")
    private Boolean planTierRequired;

    @Schema(description = "排序（越小越靠前）", example = "100")
    private Integer sequence;

    /**
     * 传入则视为全量替换 options（仅对 SELECT/MULTI_SELECT 有意义）。
     */
    @Valid
    @Schema(description = "选项列表（传入则视为全量替换）")
    private List<CreateCustomFieldReq.Option> options;
}
