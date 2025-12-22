package nan.produced.prism.core.program.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import nan.produced.prism.core.program.domain.schedule.ScheduleCommandRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScheduleCommandRuleRepositoryJpa extends JpaRepository<ScheduleCommandRuleEntity, Long> {

    List<ScheduleCommandRuleEntity> findByScheduleIdOrderByUpdatedAtDesc(UUID scheduleId);

    void deleteByScheduleId(UUID scheduleId);
}
