package nan.produced.prism.core.program.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import nan.produced.prism.core.program.domain.schedule.ScheduleAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScheduleAuditLogRepositoryJpa extends JpaRepository<ScheduleAuditLogEntity, Long> {

    List<ScheduleAuditLogEntity> findByScheduleIdOrderByCreatedAtDesc(UUID scheduleId);
}

