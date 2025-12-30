package nan.produced.prism.core.device.domain.report.sensor;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import nan.produced.prism.core.device.domain.report.sensor.type.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 传感器上报数据基类
 * <p>通过 sensorType 字段来将上报数据反序列化成对应实体类</p>
 *
 * @author Nan
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "sensorType", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ReceiveCardReport.class, name = "bitErrorRate"),
        @JsonSubTypes.Type(value = ElectromagneticReport.class, name = "electromagnetic"),
        @JsonSubTypes.Type(value = Voltage1Report.class, name = "voltage"),
        @JsonSubTypes.Type(value = Voltage2Report.class, name = "voltage2"),
        @JsonSubTypes.Type(value = RelayStatus1Report.class, name = "relayStatus"),
        @JsonSubTypes.Type(value = RelayStatus2Report.class, name = "relayStatus2"),
        @JsonSubTypes.Type(value = RelayStatus3Report.class, name = "relayStatus3"),
        @JsonSubTypes.Type(value = RelayDelay1Report.class, name = "relayDelay"),
        @JsonSubTypes.Type(value = RelayDelay2Report.class, name = "relayDelay2"),
        @JsonSubTypes.Type(value = RelayDelay3Report.class, name = "relayDelay3"),
        @JsonSubTypes.Type(value = BrightnessReport.class, name = "bright"),
        @JsonSubTypes.Type(value = NoiseReport.class, name = "noise"),
        @JsonSubTypes.Type(value = HumidityReport.class, name = "humidity"),
        @JsonSubTypes.Type(value = TemperatureReport.class, name = "temperature"),
        @JsonSubTypes.Type(value = SmokeReport.class, name = "smoke"),
        @JsonSubTypes.Type(value = Pm10Report.class, name = "pm10"),
        @JsonSubTypes.Type(value = Pm25Report.class, name = "pm25"),
        @JsonSubTypes.Type(value = GpsReport.class, name = "gps")
})
@Data
public class SensorReportBase {

    /* ======== 服务器业务添加字段 ======== */

    /**
     * 设备Id
     */
    private Long deviceId;

    /**
     * 服务器时间 - 筛选查询统一使用这个时间
     */
    private OffsetDateTime serverTime;

    /* ======== 设备上报字段 ======== */

    /**
     * 设备本地时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonProperty("date")
    private LocalDateTime reportTime;

    /**
     * 传感器Id
     */
    private Integer sensorId;

    /**
     * 传感器数据类型
     */
    private String sensorType;

}
