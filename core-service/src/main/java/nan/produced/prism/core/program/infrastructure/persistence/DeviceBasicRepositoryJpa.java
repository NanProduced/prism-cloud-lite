package nan.produced.prism.core.program.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.core.program.domain.device.DeviceBasicEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceBasicRepositoryJpa extends JpaRepository<DeviceBasicEntity, Long> {

    Optional<DeviceBasicEntity> findByDeviceIdAndUserId(Long deviceId, UUID userId);
}

