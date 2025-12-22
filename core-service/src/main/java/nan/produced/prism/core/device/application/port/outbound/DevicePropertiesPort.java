package nan.produced.prism.core.device.application.port.outbound;

import nan.produced.prism.core.device.domain.DeviceProperties;

public interface DevicePropertiesPort {

    void handleDeviceProperties(Long deviceId, DeviceProperties properties, String traceId);
}
