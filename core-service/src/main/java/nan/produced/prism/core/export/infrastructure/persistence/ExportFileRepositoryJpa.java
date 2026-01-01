package nan.produced.prism.core.export.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.core.export.domain.ExportFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExportFileRepositoryJpa extends JpaRepository<ExportFileEntity, UUID> {

    Optional<ExportFileEntity> findByIdAndUserId(UUID id, UUID userId);

    Optional<ExportFileEntity> findByTaskId(String taskId);
}

