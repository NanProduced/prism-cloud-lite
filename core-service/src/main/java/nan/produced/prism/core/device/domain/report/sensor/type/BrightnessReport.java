package nan.produced.prism.core.device.domain.report.sensor.type;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.report.sensor.SensorReportBase;

/**
 * 亮度数据上报
 *
 * @author Nan
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JsonTypeName("bright")
public class BrightnessReport extends SensorReportBase {

    /**
     * 经亮度曲线转换后的显示值(sensorId = 1000)
     */
    private Double masterBrightValue;

    /**
     * 屏幕亮度值(sensorId = 1000)
     */
    private Double screenBrightValue;

    /**
     * 传感器数据值(sensorId = 0)
     */
    private Double sensorBrightValue;

}
