package nan.produced.prism.core.device.domain.report.sensor.type;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.report.sensor.SensorReportBase;


/**
 * 电压
 * @author Nan
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JsonTypeName("voltage")
public class Voltage1Report extends SensorReportBase {

    private Double sensorValue;

}
