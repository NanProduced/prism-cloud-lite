package nan.produced.prism.core.program.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.core.program.domain.ProgramAssignmentEntity;
import nan.produced.prism.core.program.domain.ProgramAssignmentId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProgramAssignmentRepositoryJpa extends JpaRepository<ProgramAssignmentEntity, ProgramAssignmentId> {

    List<ProgramAssignmentEntity> findByProgramIdOrderByAssignedAtDesc(UUID programId);

    List<ProgramAssignmentEntity> findByDeviceIdOrderByAssignedAtDesc(Long deviceId);

    Optional<ProgramAssignmentEntity> findByProgramIdAndDeviceId(UUID programId, Long deviceId);

    Optional<ProgramAssignmentEntity> findByDeviceIdAndReleaseProgramId(Long deviceId, Integer releaseProgramId);
}

