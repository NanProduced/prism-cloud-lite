package nan.produced.prism.device.infrastructure.config.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.application.port.outbound.config.DeviceConfigPort;
import nan.produced.prism.device.application.properties.DomainDeviceProps;
import nan.produced.prism.device.infrastructure.config.properties.DeviceProps;
import org.springframework.stereotype.Component;

@Slf4j
@Component("deviceConfigPort")
@RequiredArgsConstructor
public class DeviceConfigAdapter implements DeviceConfigPort {

    private final DeviceProps deviceProps;

    @Override
    public DomainDeviceProps getDeviceConfig() {
        DomainDeviceProps domainDeviceProps = new DomainDeviceProps();

        copyOnlineStatusConfig(deviceProps.getOnlineStatus(), domainDeviceProps.getOnlineStatus());

        return domainDeviceProps;

    }

    private void copyOnlineStatusConfig(DeviceProps.OnlineStatus source,
                                        DomainDeviceProps.OnlineStatus target) {
        target.setDefaultCacheTtl(source.getDefaultCacheTtl());
        target.setReconnectCacheTtl(source.getReconnectCacheTtl());
        target.setOfflineCheckInterval(source.getOfflineCheckInterval());
        target.setOfflineCheckInitialDelay(source.getOfflineCheckInitialDelay());
        target.setCalibrationInterval(source.getCalibrationInterval());
        target.setOfflineThreshold(source.getOfflineThreshold());
        copyBufferPoolConfig(source.getBufferPool(), target.getBufferPool());
        copyStreamQueryConfig(source.getStreamQuery(), target.getStreamQuery());
    }

    private void copyBufferPoolConfig(DeviceProps.BufferPool source,
                                      DomainDeviceProps.BufferPool target) {
        target.setWindowMs(source.getWindowMs());
        target.setMaxSize(source.getMaxSize());
        target.setBatchSize(source.getBatchSize());
        target.setEmergencyFlushThreshold(source.getEmergencyFlushThreshold());
        target.setFlushTaskDelayMs(source.getFlushTaskDelayMs());
    }

    private void copyStreamQueryConfig(DeviceProps.StreamQuery source,
                                      DomainDeviceProps.StreamQuery target) {
        target.setEnabled(source.isEnabled());
        target.setPageSize(source.getPageSize());
        target.setMaxIterations(source.getMaxIterations());
        target.setTimeoutMs(source.getTimeoutMs());
    }
}
