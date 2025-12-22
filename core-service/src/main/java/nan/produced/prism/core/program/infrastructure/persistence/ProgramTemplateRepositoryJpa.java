package nan.produced.prism.core.program.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import nan.produced.prism.core.program.domain.ProgramTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProgramTemplateRepositoryJpa extends JpaRepository<ProgramTemplateEntity, UUID> {

    List<ProgramTemplateEntity> findByUserIdOrderByUpdatedAtDesc(UUID userId);
}

