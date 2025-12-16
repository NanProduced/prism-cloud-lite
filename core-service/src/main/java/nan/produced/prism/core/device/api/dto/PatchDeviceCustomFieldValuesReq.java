package nan.produced.prism.core.device.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Data;

/**
 * 设备自定义字段值批量更新请求。
 * <p>
 * values 的 key 为 fieldId（数字字符串），value 为字段值（支持 string/number/boolean/string[]/null）。
 */
@Data
public class PatchDeviceCustomFieldValuesReq {

    @NotNull
    private Map<String, Object> values;
}

