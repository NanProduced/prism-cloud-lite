package nan.produced.prism.core.program.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.core.program.domain.ProgramDraftEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProgramDraftRepositoryJpa extends JpaRepository<ProgramDraftEntity, UUID> {

    Optional<ProgramDraftEntity> findByProgramIdAndBaseVersion(UUID programId, Integer baseVersion);

    List<ProgramDraftEntity> findByProgramIdOrderByUpdatedAtDesc(UUID programId);

    List<ProgramDraftEntity> findByProgramIdInOrderByProgramIdAscUpdatedAtDesc(List<UUID> programIds);
}

