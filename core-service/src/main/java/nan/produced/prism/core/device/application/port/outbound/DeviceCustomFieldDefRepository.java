package nan.produced.prism.core.device.application.port.outbound;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.core.device.domain.customfield.DeviceCustomFieldDefEntity;

public interface DeviceCustomFieldDefRepository {

    List<DeviceCustomFieldDefEntity> findByUserId(UUID userId);

    List<DeviceCustomFieldDefEntity> findByUserIdAndFieldIdIn(UUID userId, List<Long> fieldIds);

    Optional<DeviceCustomFieldDefEntity> findByFieldIdAndUserId(Long fieldId, UUID userId);

    boolean existsByUserIdAndFieldKey(UUID userId, String fieldKey);

    DeviceCustomFieldDefEntity save(DeviceCustomFieldDefEntity entity);

    void delete(DeviceCustomFieldDefEntity entity);
}

