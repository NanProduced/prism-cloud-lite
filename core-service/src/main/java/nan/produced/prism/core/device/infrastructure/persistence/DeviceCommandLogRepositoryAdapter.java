package nan.produced.prism.core.device.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.device.application.port.outbound.DeviceCommandLogRepository;
import nan.produced.prism.core.device.domain.command.DeviceCommandLog;
import nan.produced.prism.core.device.domain.command.DeviceCommandStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeviceCommandLogRepositoryAdapter implements DeviceCommandLogRepository {

    private final DeviceCommandLogRepositoryJpa repositoryJpa;

    @Override
    public void save(DeviceCommandLog log) {

    }

    @Override
    public void saveAll(Iterable<DeviceCommandLog> logs) {

    }

    @Override
    public void updateStatus(String commandId, DeviceCommandStatus status) {

    }

    @Override
    public DeviceCommandLog findByOperationId(String operationId) {
        return null;
    }

    @Override
    public DeviceCommandLog findByDeviceIdAndQueueId(Long deviceId, Integer queueId) {
        return null;
    }
}
