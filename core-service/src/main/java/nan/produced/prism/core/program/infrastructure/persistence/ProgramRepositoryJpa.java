package nan.produced.prism.core.program.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.core.program.domain.ProgramEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProgramRepositoryJpa extends JpaRepository<ProgramEntity, UUID> {

    List<ProgramEntity> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    long countByUserId(UUID userId);

    Optional<ProgramEntity> findByIdAndUserId(UUID id, UUID userId);
}
