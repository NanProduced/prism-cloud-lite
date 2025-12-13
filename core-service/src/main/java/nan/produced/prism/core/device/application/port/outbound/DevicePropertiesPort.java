package nan.produced.prism.core.device.application.port.outbound;

public interface DevicePropertiesPort {

    void handleDeviceProperties(Long deviceId, String properties, String traceId);
}
