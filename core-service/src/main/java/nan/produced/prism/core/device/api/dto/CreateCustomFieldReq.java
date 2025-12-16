package nan.produced.prism.core.device.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;
import nan.produced.prism.core.device.domain.customfield.CustomFieldType;

@Schema(description = "创建设备自定义列请求")
@Data
public class CreateCustomFieldReq {

    @NotBlank
    @Size(max = 128)
    @Schema(description = "显示名称", example = "City")
    private String displayName;

    @NotNull
    @Schema(description = "字段类型", example = "TEXT")
    private CustomFieldType fieldType;

    /**
     * 可选：若传入将被 slug 化；为空则由 displayName 生成。
     */
    @Size(max = 128)
    @Schema(description = "字段 key（可选，若为空则由 displayName 生成并 slug 化）", example = "city")
    private String fieldKey;

    @Size(max = 512)
    @Schema(description = "描述", example = "设备所在城市")
    private String description;

    @Size(max = 64)
    @Schema(description = "图标（Lucide icon name）", example = "MapPin")
    private String icon;

    /**
     * 是否为 Pro-only 列。
     * Free 用户不允许创建 true。
     */
    @Schema(description = "是否为 Pro-only 列（Free 用户不允许创建 true）", example = "false")
    private Boolean planTierRequired = false;

    @Schema(description = "排序（越小越靠前）", example = "100")
    private Integer sequence;

    @Valid
    @Schema(description = "选项列表（仅 SELECT/MULTI_SELECT 有意义）")
    private List<Option> options;

    @Data
    @Schema(description = "自定义列选项")
    public static class Option {

        @Size(max = 128)
        @Schema(description = "选项 key（可选，若为空则由 displayName 生成并 slug 化）", example = "beijing")
        private String optionKey;

        @NotBlank
        @Size(max = 128)
        @Schema(description = "显示名称", example = "Beijing")
        private String displayName;

        @Size(max = 512)
        @Schema(description = "描述", example = "北京")
        private String description;

        @Schema(description = "排序（越小越靠前）", example = "100")
        private Integer sequence;

        @Schema(description = "是否启用", example = "true")
        private Boolean active = true;

        @Size(max = 32)
        @Schema(description = "颜色（preset key 或 hex）", example = "sky")
        private String color;
    }
}
