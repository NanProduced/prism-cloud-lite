package nan.produced.prism.core.device.domain.report.sensor.type;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.*;
import nan.produced.prism.core.device.domain.report.sensor.SensorReportBase;

import java.io.Serializable;
import java.util.List;

/**
 * GPS上报
 * @author Nan
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonTypeName("gps")
public class GpsReport extends SensorReportBase {

    /**
     * 经度
     */
    private Double longitude;

    /**
     * 纬度
     */
    private Double latitude;

    /**
     * 精度
     */
    private Float accuracy;

    /**
     * 海拔
     */
    private Float altitude;

    /**
     * 速度
     */
    private Float speed;

    /**
     * 方向
     */
    private Double direct;

    /**
     * 卫星数量
     */
    private Integer satellites;

    /**
     * 单元信息
     */
    private CellInfo cellInfo;

    /**
     * GSV信息
     */
    private List<GsvDTO> gsv;

    /**
     * GSV信息
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class GsvDTO {

        /**
         * (Azimuth) - 方位角
         */
        private Integer azi;

        /**
         * (Elevation) - 高度角（仰角）
         */
        private Integer ele;

        /**
         * (Pseudo Random Noise code) - 卫星编号
         */
        private Integer prn;

        /**
         * (Signal-to-Noise Ratio) - 信号信噪比
         */
        private Integer snr;
    }

    /**
     * 基站信息
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class CellInfo {

        /**
         * MCC (Mobile Country Code) - 移动国家代码
         */
        private Integer cid;

        /**
         * MNC (Mobile Network Code) - 移动网络代码
         */
        private Integer mnc;

        /**
         * LAC (Location Area Code) - 位置区域码
         */
        private Integer mcc;

        /*
         * CID (Cell Identity) - 基站编号（小区ID）
         */
        private Integer lac;
    }
}
