package nan.produced.prism.core.device.domain.report.sensor.type;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.report.sensor.SensorReportBase;

/**
 * 继电器延迟
 * @author Nan
 **/
@EqualsAndHashCode(callSuper = true)
@Data
@JsonTypeName("relayDelay")
public class RelayDelay1Report extends SensorReportBase {

    private Double sensorValue;

}
