package nan.produced.prism.core.device.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.core.device.domain.customfield.DeviceCustomFieldValueEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceCustomFieldValueRepositoryJpa extends JpaRepository<DeviceCustomFieldValueEntity, Long> {

    Optional<DeviceCustomFieldValueEntity> findByUserIdAndDeviceIdAndFieldId(UUID userId, Long deviceId, Long fieldId);

    List<DeviceCustomFieldValueEntity> findByUserIdAndDeviceIdAndFieldIdIn(UUID userId, Long deviceId, List<Long> fieldIds);

    List<DeviceCustomFieldValueEntity> findByUserIdAndDeviceIdIn(UUID userId, List<Long> deviceIds);

    void deleteByUserIdAndDeviceIdAndFieldId(UUID userId, Long deviceId, Long fieldId);

    void deleteByUserIdAndFieldId(UUID userId, Long fieldId);
}
