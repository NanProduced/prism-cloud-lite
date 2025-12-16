package nan.produced.prism.core.device.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nan.produced.prism.core.device.domain.customfield.CustomFieldType;

@Schema(description = "设备自定义列定义（Grid 视图列管理）")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeviceCustomFieldDefVO {

    @Schema(description = "字段ID", example = "10001")
    private Long fieldId;

    @Schema(description = "字段唯一标识（slug），用于设备值 map 的 key", example = "city")
    private String fieldKey;

    @Schema(description = "字段类型", example = "TEXT")
    private CustomFieldType fieldType;

    @Schema(description = "显示名称", example = "City")
    private String displayName;

    @Schema(description = "描述", example = "设备所在城市")
    private String description;

    @Schema(description = "图标（Lucide icon name）", example = "MapPin")
    private String icon;

    @Schema(description = "是否为 Pro-only 列", example = "false")
    private Boolean planTierRequired;

    @Schema(description = "排序（越小越靠前）", example = "100")
    private Integer sequence;

    @Schema(description = "选项列表（仅 SELECT/MULTI_SELECT 有值）")
    private List<DeviceCustomFieldOptionVO> options;
}
