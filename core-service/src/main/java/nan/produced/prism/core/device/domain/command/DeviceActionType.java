package nan.produced.prism.core.device.domain.command;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import nan.produced.prism.core.device.application.service.DeviceEventApplicationService;

import java.time.Instant;
import java.util.Map;

/**
 * 设备操作（指令）类型
 *
 * @see nan.produced.prism.device.application.domain.command.DeviceCommand
 * @author Nan
 */
@Getter
@Schema(description = "设备动作类型")
public enum DeviceActionType {

    @Schema(description = "亮度调节（PUT api/brightness）")
    BRIGHTNESS("api/brightness", DeviceCommandMethod.PUT, DeviceActionTrackingLevel.PROPERTY_MATCH),

    /**
     * 这个指令没有对应上报（观察在线状态即可）
     */
    @Schema(description = "电源控制-休眠/唤醒/重启 （POST api/action）")
    POWER("api/action", DeviceCommandMethod.POST, DeviceActionTrackingLevel.ACK_ONLY),

    @Schema(description = "色温调节（PUT api/colortemp）")
    COLOR_TEMP("api/colortemp", DeviceCommandMethod.PUT, DeviceActionTrackingLevel.PROPERTY_MATCH),

    @Schema(description = "音量调节（PUT api/volume）")
    VOLUME("api/volume", DeviceCommandMethod.PUT, DeviceActionTrackingLevel.PROPERTY_MATCH),

    @Schema(description = "清除缓存（DELETE api/clrresunused）")
    CLEAR_CACHE("api/clrresunused", DeviceCommandMethod.DELETE, DeviceActionTrackingLevel.PROPERTY_MATCH),

    @Schema(description = "切换信号源:HDMI/DVI （PUT api/inputmode）")
    INPUT_MODE("api/inputmode", DeviceCommandMethod.PUT, DeviceActionTrackingLevel.PROPERTY_MATCH),

    @Schema(description = "时区/时间设置（PUT api/newrtc）")
    TIMEZONE("api/newrtc", DeviceCommandMethod.PUT, DeviceActionTrackingLevel.PROPERTY_MATCH),

    @Schema(description = "地区/语言设置（PUT api/locale）")
    LOCALE("api/locale", DeviceCommandMethod.PUT, DeviceActionTrackingLevel.PROPERTY_MATCH),

    @Schema(description = "素材/节目统计上报开关设置（PUT api/contentreport）")
    CONTENT_REPORT_SWITCH("api/contentreport", DeviceCommandMethod.PUT, DeviceActionTrackingLevel.PROPERTY_MATCH),

    /**
     * 根据截图上报推送SSE
     * <p>逻辑实现在 {@link DeviceEventApplicationService#handleScreenshotUploaded(Long, Map, String, Instant)} 中。</p>
     */
    @Schema(description = "屏幕截图（POST api/transmission/ftp/config）")
    SCREENSHOT("api/transmission/ftp/config", DeviceCommandMethod.POST, DeviceActionTrackingLevel.UPDATE_ONLY),

    @Schema(description = "传感器上报时间设置（POST api/setreporttime）")
    SET_SENSOR_REPORT_TIME("api/setreporttime", DeviceCommandMethod.POST, DeviceActionTrackingLevel.UPDATE_ONLY),

    @Schema(description = "清除设备上的节目，即清除设备上当前已下载的节目（POST api/clrprgms）")
    CLEAR_DEVICE_PROGRAM("api/clrprgms", DeviceCommandMethod.POST, DeviceActionTrackingLevel.UPDATE_ONLY);

    /**
     * 对应DeviceCommand.authorUrl
     * <p>注意：由于设备端逻辑，url中的api前不需要加'/'</p>
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
