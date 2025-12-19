package nan.produced.prism.core.device.domain.command;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 设备操作（指令）类型
 *
 * @see nan.produced.prism.device.application.domain.command.DeviceCommand
 * @author Nan
 */
@Getter
@Schema(description = "设备动作类型")
public enum DeviceActionType {

    @Schema(description = "亮度调节（PUT /api/brightness）")
    BRIGHTNESS("/api/brightness", DeviceCommandMethod.PUT, DeviceActionTrackingLevel.PROPERTY_MATCH);


    /**
     * 对应DeviceCommand.authorUrl
     */
    private final String url;

    /**
     * 对应DeviceCommand.karma
     */
    private final DeviceCommandMethod method;

    /**
     * 动作追踪等级（用于决定是否需要追踪 ACK/属性匹配/显式结果）
     */
    private final DeviceActionTrackingLevel trackingLevel;

    DeviceActionType(String url, DeviceCommandMethod method, DeviceActionTrackingLevel trackingLevel) {
        this.url = url;
        this.method = method;
        this.trackingLevel = trackingLevel;
    }

    /**
     * 对应 DeviceCommand.karma（保留给下游适配层使用）
     */
    public int getKarma() {
        return method.getKarma();
    }
}
