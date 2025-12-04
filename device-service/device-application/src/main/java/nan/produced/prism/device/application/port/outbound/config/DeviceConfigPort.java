package nan.produced.prism.device.application.port.outbound.config;

import nan.produced.prism.device.application.properties.DeviceProperties;

public interface DeviceConfigPort {

    /**
     * 获取设备配置
     * @return 设备配置属性
     */
    DeviceProperties getDeviceConfig();
}
