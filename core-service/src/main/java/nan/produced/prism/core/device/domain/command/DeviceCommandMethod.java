package nan.produced.prism.core.device.domain.command;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 设备侧 API HTTP Method（对应 device-service 的 {@code DeviceCommand.karma} 约定）
 * <p>
 * karma 映射：
 * - 0 GET
 * - 1 POST
 * - 2 PUT
 * - 3 DELETE
 */
@Schema(description = "设备 API HTTP Method（对应 DeviceCommand.karma）")
@Getter
public enum DeviceCommandMethod {

    GET(0),
    POST(1),
    PUT(2),
    DELETE(3);

    /**
     * 对应 DeviceCommand.karma
     */
    private final int karma;

    DeviceCommandMethod(int karma) {
        this.karma = karma;
    }
}

