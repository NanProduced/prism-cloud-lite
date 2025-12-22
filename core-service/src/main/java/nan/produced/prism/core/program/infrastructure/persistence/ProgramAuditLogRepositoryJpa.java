package nan.produced.prism.core.program.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import nan.produced.prism.core.program.domain.ProgramAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProgramAuditLogRepositoryJpa extends JpaRepository<ProgramAuditLogEntity, Long> {

    List<ProgramAuditLogEntity> findByProgramIdOrderByCreatedAtDesc(UUID programId);
}

