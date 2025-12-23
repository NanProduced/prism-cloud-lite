package nan.produced.prism.core.device.domain.report.sensor.type;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.report.sensor.SensorReportBase;

/**
 * 继电器 2 上报
 * @author Nan
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JsonTypeName("relayStatus2")
public class RelayStatus2Report extends SensorReportBase {

    private Double sensorValue;
}
