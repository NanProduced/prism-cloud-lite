package nan.produced.prism.core.device.domain.report.sensor.type;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import nan.produced.prism.core.device.domain.report.sensor.SensorReportBase;

import java.util.List;

/**
 * 接收卡上报
 * @author Nan
 */
@EqualsAndHashCode(callSuper = true)
@Data
@JsonTypeName("bitErrorRate")
public class ReceiveCardReport extends SensorReportBase {

    /**
     * 上报数据
     */
    private List<ReceiveCardValue> sensorValue;

    @Data
    public static class ReceiveCardValue {

        /**
         * 接收卡信息
         */
        private List<ReceiveCardInfo> receiveCards;

        /**
         * 网口
         */
        private Integer netPortNum;

    }

    @Data
    public static class ReceiveCardInfo {

        private Integer receiveCardNum;

        private Double smoke;

        private Double bitErrorRate;

        private Integer height;

        private Integer humidity;

        private Integer temperature;

        private  Integer width;

        private Integer x;

        private Integer y;
    }
}
