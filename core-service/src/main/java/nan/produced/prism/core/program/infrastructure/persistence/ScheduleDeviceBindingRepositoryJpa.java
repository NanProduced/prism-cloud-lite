package nan.produced.prism.core.program.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.core.program.domain.schedule.ScheduleDeviceBindingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScheduleDeviceBindingRepositoryJpa extends JpaRepository<ScheduleDeviceBindingEntity, Long> {

    Optional<ScheduleDeviceBindingEntity> findByDeviceId(Long deviceId);

    List<ScheduleDeviceBindingEntity> findByScheduleId(UUID scheduleId);
}

