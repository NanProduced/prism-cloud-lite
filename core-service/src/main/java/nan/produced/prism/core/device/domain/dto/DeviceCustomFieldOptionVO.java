package nan.produced.prism.core.device.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "设备自定义列选项（用于 SELECT/MULTI_SELECT）")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeviceCustomFieldOptionVO {

    @Schema(description = "选项ID", example = "20001")
    private Long optionId;

    @Schema(description = "选项唯一标识（slug）", example = "beijing")
    private String optionKey;

    @Schema(description = "选项显示名称", example = "Beijing")
    private String displayName;

    @Schema(description = "选项描述", example = "北京")
    private String description;

    @Schema(description = "排序（越小越靠前）", example = "100")
    private Integer sequence;

    @Schema(description = "是否启用", example = "true")
    private Boolean active;

    @Schema(description = "颜色（preset key 或 hex）", example = "sky")
    private String color;
}
