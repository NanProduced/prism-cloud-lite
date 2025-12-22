package nan.produced.prism.core.device.domain.command.body;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.command.DeviceActionBodyBase;

@Schema(description = "清除缓存参数")
@Data
@EqualsAndHashCode(callSuper = true)
public class ClearCacheBody extends DeviceActionBodyBase {

    // 这个指令就是content为空
}
