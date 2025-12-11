package nan.produced.prism.device.application.messaging;

/**
 * RabbitMq Routing Keys
 *
 * @author Nan
 */
public final class DeviceEventRoutingKeys {

    /**
     * 设备在线状态routing keys
     */
    private static final String STATUS_PREFIX = "status.";

    /**
     * 设备指令响应routing keys
     */
    private static final String COMMAND_PREFIX = "command.";

    /**
     * 设备上报数据routing keys
     */
    private static final String REPORT_PREFIX = "report.";

    private DeviceEventRoutingKeys() {
    }

    public static String status(String type) {
        return STATUS_PREFIX + sanitize(type);
    }

    public static String command(String type) {
        return COMMAND_PREFIX + sanitize(type);
    }

    public static String report(String type) {
        return REPORT_PREFIX + sanitize(type);
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "generic";
        }
        return value.replaceAll("\s+", "-").toLowerCase();
    }
}

