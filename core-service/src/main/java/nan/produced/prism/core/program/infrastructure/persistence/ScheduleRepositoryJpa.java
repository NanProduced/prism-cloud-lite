package nan.produced.prism.core.program.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.core.program.domain.schedule.ScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScheduleRepositoryJpa extends JpaRepository<ScheduleEntity, UUID> {

    List<ScheduleEntity> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<ScheduleEntity> findByScheduleIdAndUserId(UUID scheduleId, UUID userId);
}

