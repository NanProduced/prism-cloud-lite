package nan.produced.prism.core.device.domain.report.sensor;

import lombok.Getter;

/**
 * 传感器上报数据类型
 * <p>其中sensorType、sensorId属于设备上报的字段，由于区分是哪个数据源上报的哪种数据</p>
 * <p>reportType和sensorSourceType是服务器接收到后在业务层面做的区分，用于前端展示和数据过滤、筛选</p>
 *
 * @author Nan
 */
@Getter
public enum SensorReportType {

    /**
     * 接收卡数据
     */
    RECEIVE_CARD("bitErrorRate", null, "receiveCard", SensorType.RECEIVE_CARD),
    /**
     * 电磁门
     */
    ELECTROMAGNETIC("electromagnetic", 9999, "electromagnetic", SensorType.M2_SENSOR),
    /**
     * 电压
     */
    VOLTAGE("voltage", 9999, "voltage", SensorType.M2_SENSOR),
    /**
     * 电压 2
     */
    VOLTAGE2("voltage2", 9999, "voltage2", SensorType.M2_SENSOR),
    /**
     * 继电器
     */
    RELAY_STATUS("relayStatus", 9999, "relayStatus", SensorType.M2_SENSOR),
    /**
     * 继电器 2
     */
    RELAY_STATUS2("relayStatus2", 9999, "relayStatus2", SensorType.M2_SENSOR),
    /**
     * 继电器 3
     */
    RELAY_STATUS3("relayStatus3", 9999, "relayStatus3", SensorType.M2_SENSOR),
    /**
     * 继电器延迟
     */
    RELAY_DELAY("relayDelay", 9999, "relayDelay", SensorType.M2_SENSOR),
    /**
     * 继电器延迟 2
     */
    RELAY_DELAY2("relayDelay2", 9999, "relayDelay2", SensorType.M2_SENSOR),
    /**
     * 继电器延迟 3
     */
    RELAY_DELAY3("relayDelay3", 9999, "relayDelay3", SensorType.M2_SENSOR),
    /**
     * 传感器亮度
     */
    BRIGHTNESS("bright", 0, "bright", SensorType.DEVICE_SENSOR),
    /**
     * 屏幕亮度
     */
    SCREEN_BRIGHTNESS("bright", 1000, "bright", SensorType.M2_SENSOR),
    /**
     * 传感器噪声
     */
    NOISE("noise", 1, "noise", SensorType.DEVICE_SENSOR),
    /**
     * M2传感器噪声
     */
    M2_NOISE("noise", 1001, "noise", SensorType.M2_SENSOR),
    /**
     * 传感器湿度
     */
    HUMIDITY("humidity", 2, "humidity", SensorType.DEVICE_SENSOR),
    /**
     * M2传感器湿度
     */
    M2_HUMIDITY("humidity", 1002, "humidity", SensorType.M2_SENSOR),
    /**
     * 传感器温度
     */
    TEMPERATURE("temperature", 2, "temperature", SensorType.DEVICE_SENSOR),
    /**
     * M2传感器温度
     */
    M2_TEMPERATURE("temperature", 1002, "temperature", SensorType.M2_SENSOR),
    /**
     * 传感器烟雾
     */
    SMOKE("smoke", 3, "smoke", SensorType.DEVICE_SENSOR),
    /**
     * M2传感器烟雾
     */
    M2_SMOKE("smoke", 1003, "smoke", SensorType.M2_SENSOR),
    /**
     * 传感器Pm10
     */
    PM10("pm10", 4, "pm10", SensorType.DEVICE_SENSOR),
    /**
     * M2传感器Pm10
     */
    M2_PM10("pm10", 1004, "pm10",SensorType.M2_SENSOR),
    /**
     * Pm25
     */
    PM25("pm25", 4, "pm25", SensorType.DEVICE_SENSOR),
    /**
     * M2传感器Pm25
     */
    M2_PM25("pm25", 1004, "pm25", SensorType.M2_SENSOR),
    /**
     * GPS
     */
    GPS("gps", 20, "gps", SensorType.GPS),
    /**
     * A20板载湿度
     */
    A20_HUMIDITY_ON_BOARD("humidity", 7, "humidityOnBoard", SensorType.DEVICE_SENSOR),
    /**
     * M2板载湿度
     */
    M2_HUMIDITY_ON_BOARD("humidity", 2002, "humidityOnBoard", SensorType.M2_SENSOR),
    /**
     * A20板载温度
     */
    A20_TEMPERATURE_ON_BOARD("temperature", 6, "temperatureOnBoard", SensorType.DEVICE_SENSOR),
    /**
     * M2板载温度
     */
    M2_TEMPERATURE_ON_BOARD("temperature", 2002, "temperatureOnBoard", SensorType.M2_SENSOR);


    /* ========= 设备上报数据区分 ========= */

    /**
     * 设备上报的sensorType字段
     */
    private final String sensorType;

    /**
     * 设备上报的sensorId字段
     */
    private final Integer sensorId;

    /* ========= 业务层面数据类型区分 ========= */

    /**
     * 传感器数据类型
     */
    private final String reportType;

    /**
     * 传感器数据来源类型(设备自带或外接M2传感器)
     */
    private final SensorType sensorSourceType;

    SensorReportType(String sensorType, Integer sensorId, String reportType, SensorType sensorSourceType) {
        this.sensorType = sensorType;
        this.sensorId = sensorId;
        this.reportType = reportType;
        this.sensorSourceType = sensorSourceType;
    }

    public static SensorReportType fromSensorType(String sensorType) {
        return resolve(sensorType, null);
    }

    /**
     * 通过设备上报的 sensorType + sensorId 精确解析到业务类型。
     *
     * <p>说明：同一 sensorType 下会存在多个传感器来源/含义（如 bright/humidity/temperature 等），
     * 需结合 sensorId 才能正确区分（DEVICE_SENSOR vs M2_SENSOR vs 板载）。</p>
     */
    public static SensorReportType resolve(String sensorType, Integer sensorId) {
        if (sensorType == null || sensorType.isBlank()) {
            return null;
        }

        String normalizedType = sensorType.trim();

        // 1) 优先精确匹配（sensorType + sensorId）
        if (sensorId != null) {
            for (SensorReportType type : SensorReportType.values()) {
                if (normalizedType.equals(type.getSensorType())
                        && type.getSensorId() != null
                        && sensorId.equals(type.getSensorId())) {
                    return type;
                }
            }
        }

        // 2) 兜底：仅匹配 sensorType（用于不区分 sensorId 的类型，例如接收卡 bitErrorRate）
        for (SensorReportType type : SensorReportType.values()) {
            if (normalizedType.equals(type.getSensorType()) && type.getSensorId() == null) {
                return type;
            }
        }

        // 3) 最后兜底：按 sensorType 返回第一个（尽量不返回 null，便于兼容旧逻辑）
        for (SensorReportType type : SensorReportType.values()) {
            if (normalizedType.equals(type.getSensorType())) {
                return type;
            }
        }

        return null;
    }
}
