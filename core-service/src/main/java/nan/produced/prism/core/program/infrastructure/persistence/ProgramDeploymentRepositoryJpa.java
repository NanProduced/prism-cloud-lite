package nan.produced.prism.core.program.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.core.program.domain.ProgramDeploymentEntity;
import nan.produced.prism.core.program.domain.ProgramDeploymentId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProgramDeploymentRepositoryJpa extends JpaRepository<ProgramDeploymentEntity, ProgramDeploymentId> {

    List<ProgramDeploymentEntity> findByProgramIdOrderByAssignedAtDesc(UUID programId);

    List<ProgramDeploymentEntity> findByUserIdOrderByAssignedAtDesc(UUID userId);

    Optional<ProgramDeploymentEntity> findByProgramIdAndDeviceId(UUID programId, Long deviceId);

    List<ProgramDeploymentEntity> findByDeviceIdOrderByAssignedAtDesc(Long deviceId);

    Optional<ProgramDeploymentEntity> findByDeviceIdAndReleaseProgramId(Long deviceId, Integer releaseProgramId);

    void deleteByDeviceId(Long deviceId);
}
