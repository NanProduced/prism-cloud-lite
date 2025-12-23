package nan.produced.prism.core.device.domain.report.sensor.type;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.report.sensor.SensorReportBase;

/**
 * PM25 上报
 * @author Nan
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JsonTypeName("pm25")
public class Pm25Report extends SensorReportBase {

    private Double sensorValue;
}
