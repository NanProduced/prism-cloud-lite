package nan.produced.prism.core.program.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import nan.produced.prism.core.program.domain.schedule.ScheduleContentsRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScheduleContentsRuleRepositoryJpa extends JpaRepository<ScheduleContentsRuleEntity, Long> {

    List<ScheduleContentsRuleEntity> findByScheduleIdOrderByPriorityAsc(UUID scheduleId);

    boolean existsByScheduleIdAndReleaseProgramId(UUID scheduleId, Integer releaseProgramId);

    void deleteByScheduleId(UUID scheduleId);
}
