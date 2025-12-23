package nan.produced.prism.core.device.domain.report.sensor.type;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.report.sensor.SensorReportBase;

/**
 * 湿度曲线
 *
 * @author Nan
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JsonTypeName("humidity")
public class HumidityReport extends SensorReportBase {

    /**
     * 传感器数据值
     */
    private Double sensorValue;
}
