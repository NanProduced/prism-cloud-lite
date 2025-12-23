package nan.produced.prism.core.device.domain.report.sensor;

public enum SensorType {

    /**
     * 设备传感器
     */
    DEVICE_SENSOR,

    /**
     * 接收卡传感器
     */
    RECEIVE_CARD,

    /**
     * GPS
     */
    GPS,

    /**
     * 外接传感器 - 采用M.2(NGFF)接口标准的传感器模块
     */
    M2_SENSOR;
}
