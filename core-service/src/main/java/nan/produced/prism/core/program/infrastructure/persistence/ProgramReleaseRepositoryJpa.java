package nan.produced.prism.core.program.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.core.program.domain.ProgramReleaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProgramReleaseRepositoryJpa extends JpaRepository<ProgramReleaseEntity, Integer> {

    Optional<ProgramReleaseEntity> findTopByProgramIdOrderByVersionDesc(UUID programId);

    List<ProgramReleaseEntity> findByProgramIdOrderByVersionDesc(UUID programId);

    Optional<ProgramReleaseEntity> findByProgramIdAndVersion(UUID programId, Integer version);

    List<ProgramReleaseEntity> findByProgramIdInOrderByProgramIdAscVersionDesc(List<UUID> programIds);

    @Query("""
            SELECT r
            FROM ProgramReleaseEntity r, ProgramEntity p
            WHERE p.id = r.programId
              AND p.userId = :userId
              AND r.vsnMd5 = :vsnMd5
              AND r.vsnSizeBytes = :vsnSizeBytes
            """)
    Optional<ProgramReleaseEntity> findByUserIdAndVsnMd5AndVsnSizeBytes(
            @Param("userId") UUID userId,
            @Param("vsnMd5") String vsnMd5,
            @Param("vsnSizeBytes") Long vsnSizeBytes);
}
