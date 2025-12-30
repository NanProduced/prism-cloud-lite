package nan.produced.prism.core.program.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import nan.produced.prism.core.program.domain.schedule.ScheduleContentsRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ScheduleContentsRuleRepositoryJpa extends JpaRepository<ScheduleContentsRuleEntity, Long> {

    List<ScheduleContentsRuleEntity> findByScheduleIdOrderByPriorityAsc(UUID scheduleId);

    boolean existsByScheduleIdAndReleaseProgramId(UUID scheduleId, Integer releaseProgramId);

    @Query("""
            SELECT DISTINCT r.scheduleId
              FROM ScheduleContentsRuleEntity r
             WHERE r.releaseProgramId IN :releaseProgramIds
            """)
    List<UUID> findDistinctScheduleIdsByReleaseProgramIdIn(@Param("releaseProgramIds") List<Integer> releaseProgramIds);

    void deleteByScheduleId(UUID scheduleId);
}
