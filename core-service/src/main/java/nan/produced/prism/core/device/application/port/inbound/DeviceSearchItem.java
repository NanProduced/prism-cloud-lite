package nan.produced.prism.core.device.application.port.inbound;

/**
 * 全局搜索使用的设备轻量结果项。
 */
public record DeviceSearchItem(
        Long id,
        String name,
        String serialNo,
        String ip,
        Integer onlineStatus,
        String model
) {
}

