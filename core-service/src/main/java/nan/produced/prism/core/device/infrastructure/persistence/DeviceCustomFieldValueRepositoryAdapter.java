package nan.produced.prism.core.device.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.device.application.port.outbound.DeviceCustomFieldValueRepository;
import nan.produced.prism.core.device.domain.customfield.DeviceCustomFieldValueEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeviceCustomFieldValueRepositoryAdapter implements DeviceCustomFieldValueRepository {

    private final DeviceCustomFieldValueRepositoryJpa repositoryJpa;

    @Override
    public Optional<DeviceCustomFieldValueEntity> findByUserIdAndDeviceIdAndFieldId(UUID userId, Long deviceId, Long fieldId) {
        return repositoryJpa.findByUserIdAndDeviceIdAndFieldId(userId, deviceId, fieldId);
    }

    @Override
    public List<DeviceCustomFieldValueEntity> findByUserIdAndDeviceIdAndFieldIdIn(UUID userId, Long deviceId, List<Long> fieldIds) {
        return repositoryJpa.findByUserIdAndDeviceIdAndFieldIdIn(userId, deviceId, fieldIds);
    }

    @Override
    public void deleteByUserIdAndDeviceIdAndFieldId(UUID userId, Long deviceId, Long fieldId) {
        repositoryJpa.deleteByUserIdAndDeviceIdAndFieldId(userId, deviceId, fieldId);
    }

    @Override
    public void deleteByUserIdAndFieldId(UUID userId, Long fieldId) {
        repositoryJpa.deleteByUserIdAndFieldId(userId, fieldId);
    }

    @Override
    public DeviceCustomFieldValueEntity save(DeviceCustomFieldValueEntity entity) {
        return repositoryJpa.save(entity);
    }
}

