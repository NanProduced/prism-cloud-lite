package nan.produced.prism.core.device.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Data;

/**
 * 设备自定义字段值批量更新请求。
 * <p>
 * values 的 key 为 fieldId（数字字符串），value 为字段值（支持 string/number/boolean/string[]/null）。
 */
@Schema(description = "设备自定义列值批量更新请求")
@Data
public class PatchDeviceCustomFieldValuesReq {

    @NotNull
    @Schema(description = "字段值 map：key 为 fieldId（数字字符串），value 为字段值（支持 string/number/boolean/string[]/null）")
    private Map<String, Object> values;
}
