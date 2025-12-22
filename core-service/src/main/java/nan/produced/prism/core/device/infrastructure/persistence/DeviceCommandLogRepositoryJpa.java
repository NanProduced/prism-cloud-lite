package nan.produced.prism.core.device.infrastructure.persistence;

import nan.produced.prism.core.device.domain.command.DeviceCommandLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceCommandLogRepositoryJpa extends JpaRepository<DeviceCommandLog, Long> {
}
