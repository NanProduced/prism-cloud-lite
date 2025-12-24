package nan.produced.prism.core.telemetry.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.core.telemetry.domain.DeviceLocationOverrideEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceLocationOverrideRepositoryJpa extends JpaRepository<DeviceLocationOverrideEntity, Long> {

    Optional<DeviceLocationOverrideEntity> findByUserIdAndDeviceId(UUID userId, Long deviceId);

    List<DeviceLocationOverrideEntity> findByUserId(UUID userId);
}

