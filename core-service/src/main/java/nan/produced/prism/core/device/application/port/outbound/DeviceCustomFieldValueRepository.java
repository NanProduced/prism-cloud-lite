package nan.produced.prism.core.device.application.port.outbound;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.core.device.domain.customfield.DeviceCustomFieldValueEntity;

public interface DeviceCustomFieldValueRepository {

    Optional<DeviceCustomFieldValueEntity> findByUserIdAndDeviceIdAndFieldId(UUID userId, Long deviceId, Long fieldId);

    List<DeviceCustomFieldValueEntity> findByUserIdAndDeviceIdAndFieldIdIn(UUID userId, Long deviceId, List<Long> fieldIds);

    void deleteByUserIdAndDeviceIdAndFieldId(UUID userId, Long deviceId, Long fieldId);

    void deleteByUserIdAndFieldId(UUID userId, Long fieldId);

    DeviceCustomFieldValueEntity save(DeviceCustomFieldValueEntity entity);
}

