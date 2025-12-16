package nan.produced.prism.core.device.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.device.application.port.outbound.DeviceCustomFieldDefRepository;
import nan.produced.prism.core.device.domain.customfield.DeviceCustomFieldDefEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeviceCustomFieldDefRepositoryAdapter implements DeviceCustomFieldDefRepository {

    private final DeviceCustomFieldDefRepositoryJpa repositoryJpa;

    @Override
    public List<DeviceCustomFieldDefEntity> findByUserId(UUID userId) {
        return repositoryJpa.findByUserId(userId);
    }

    @Override
    public List<DeviceCustomFieldDefEntity> findByUserIdAndFieldIdIn(UUID userId, List<Long> fieldIds) {
        return repositoryJpa.findByUserIdAndFieldIdIn(userId, fieldIds);
    }

    @Override
    public Optional<DeviceCustomFieldDefEntity> findByFieldIdAndUserId(Long fieldId, UUID userId) {
        return repositoryJpa.findByFieldIdAndUserId(fieldId, userId);
    }

    @Override
    public boolean existsByUserIdAndFieldKey(UUID userId, String fieldKey) {
        return repositoryJpa.existsByUserIdAndFieldKey(userId, fieldKey);
    }

    @Override
    public DeviceCustomFieldDefEntity save(DeviceCustomFieldDefEntity entity) {
        return repositoryJpa.save(entity);
    }

    @Override
    public void delete(DeviceCustomFieldDefEntity entity) {
        repositoryJpa.delete(entity);
    }
}

