package nan.produced.prism.core.device.domain.command;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import nan.produced.prism.core.device.domain.command.action.*;

@Schema(
        description = "设备动作（单入口多动作）",
        discriminatorProperty = "type",
        oneOf = {
                BrightnessAction.class,
                PowerAction.class,
                ColorTempAction.class,
                VolumeAction.class,
                ClearCacheAction.class,
                InputModeAction.class,
                TimezoneAction.class,
                LocaleAction.class,
                 ContentReportSwitchAction.class,
                 ScreenshotAction.class,
                 SensorReportTimeAction.class,
                 DeleteDeviceVsnAction.class,
                 ClearProgramAction.class
         },
        discriminatorMapping = {
                @DiscriminatorMapping(value = "BRIGHTNESS", schema = BrightnessAction.class),
                @DiscriminatorMapping(value = "POWER", schema = PowerAction.class),
                @DiscriminatorMapping(value = "COLOR_TEMP", schema = ColorTempAction.class),
                @DiscriminatorMapping(value = "VOLUME", schema = VolumeAction.class),
                @DiscriminatorMapping(value = "CLEAR_CACHE", schema = ClearCacheAction.class),
                @DiscriminatorMapping(value = "INPUT_MODE", schema = InputModeAction.class),
                @DiscriminatorMapping(value = "TIMEZONE", schema = TimezoneAction.class),
                @DiscriminatorMapping(value = "LOCALE", schema = LocaleAction.class),
                @DiscriminatorMapping(value = "CONTENT_REPORT_SWITCH", schema = ContentReportSwitchAction.class),
                 @DiscriminatorMapping(value = "SCREENSHOT", schema = ScreenshotAction.class),
                 @DiscriminatorMapping(value = "SET_SENSOR_REPORT_TIME", schema = SensorReportTimeAction.class),
                 @DiscriminatorMapping(value = "DELETE_DEVICE_VSN", schema = DeleteDeviceVsnAction.class),
                 @DiscriminatorMapping(value = "CLEAR_DEVICE_PROGRAM", schema = ClearProgramAction.class)
         }
)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = BrightnessAction.class, name = "BRIGHTNESS"),
        @JsonSubTypes.Type(value = PowerAction.class, name = "POWER"),
        @JsonSubTypes.Type(value = ColorTempAction.class, name = "COLOR_TEMP"),
        @JsonSubTypes.Type(value = VolumeAction.class, name = "VOLUME"),
        @JsonSubTypes.Type(value = ClearCacheAction.class, name = "CLEAR_CACHE"),
        @JsonSubTypes.Type(value = InputModeAction.class, name = "INPUT_MODE"),
        @JsonSubTypes.Type(value = TimezoneAction.class, name = "TIMEZONE"),
        @JsonSubTypes.Type(value = LocaleAction.class, name = "LOCALE"),
        @JsonSubTypes.Type(value = ContentReportSwitchAction.class, name = "CONTENT_REPORT_SWITCH"),
         @JsonSubTypes.Type(value = ScreenshotAction.class, name = "SCREENSHOT"),
         @JsonSubTypes.Type(value = SensorReportTimeAction.class, name = "SET_SENSOR_REPORT_TIME"),
         @JsonSubTypes.Type(value = DeleteDeviceVsnAction.class, name = "DELETE_DEVICE_VSN"),
         @JsonSubTypes.Type(value = ClearProgramAction.class, name = "CLEAR_DEVICE_PROGRAM")
})
@Data
public abstract class DeviceActionBase {

    @NotNull
    @Schema(description = "动作类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "BRIGHTNESS")
    private DeviceActionType type;

    @Schema(description = "指令在 device-service 中的缓存 TTL（分钟）；为空时由服务端使用默认值", example = "60")
    private Long ttlMinutes;

    @Schema(description = "客户端请求幂等键（可选，用于避免重复点击）", example = "req_20251219_0001")
    private String clientRequestId;

    @Valid
    @Schema(description = "动作参数（不同 type 的 body 结构不同）", requiredMode = Schema.RequiredMode.REQUIRED)
    public abstract DeviceActionBodyBase getBody();
}
