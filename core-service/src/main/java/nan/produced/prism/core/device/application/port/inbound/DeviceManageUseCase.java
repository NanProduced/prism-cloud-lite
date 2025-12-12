package nan.produced.prism.core.device.application.port.inbound;

import nan.produced.prism.core.device.domain.dto.CreateDeviceDTO;

public interface DeviceManageUseCase {

    /**
     * 创建设备
     * @param createDeviceDTO 创建设备数据传输对象
     * @return 设备ID
     */
    public Long createDevice(CreateDeviceDTO createDeviceDTO);
}
