package nan.produced.prism.core.device.infrastructure.persistence;

import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.device.application.port.outbound.DeviceCommandLogRepository;
import nan.produced.prism.core.device.domain.command.DeviceCommandLog;
import nan.produced.prism.core.device.domain.command.DeviceCommandStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class DeviceCommandLogRepositoryAdapter implements DeviceCommandLogRepository {

    private final DeviceCommandLogRepositoryJpa repositoryJpa;

    @Override
    public void save(DeviceCommandLog log) {
        if (log == null) {
            return;
        }
        repositoryJpa.save(log);
    }

    @Override
    public void saveAll(Iterable<DeviceCommandLog> logs) {
        if (logs == null) {
            return;
        }
        repositoryJpa.saveAll(logs);
    }

    @Override
    public void updateStatus(String commandId, DeviceCommandStatus status) {
        if (!StringUtils.hasText(commandId) || status == null) {
            return;
        }
        repositoryJpa.updateStatus(commandId, status, OffsetDateTime.now());
    }

    @Override
    public DeviceCommandLog findByOperationId(String operationId) {
        if (!StringUtils.hasText(operationId)) {
            return null;
        }
        return repositoryJpa.findByOperationId(operationId).orElse(null);
    }

    @Override
    public DeviceCommandLog findByDeviceIdAndQueueId(Long deviceId, Integer queueId) {
        if (deviceId == null || queueId == null) {
            return null;
        }
        return repositoryJpa.findFirstByDeviceIdAndQueuedIdOrderByCreatedAtDesc(deviceId, queueId).orElse(null);
    }
}
