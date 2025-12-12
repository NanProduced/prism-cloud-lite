package nan.produced.prism.core.device.application.port.outbound;

import nan.produced.prism.core.device.domain.DeviceEntity;

public interface DeviceRepository {

    void createDevice(DeviceEntity entity);
}
