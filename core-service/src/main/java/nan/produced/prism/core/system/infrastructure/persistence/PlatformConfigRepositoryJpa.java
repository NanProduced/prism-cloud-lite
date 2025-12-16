package nan.produced.prism.core.system.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.core.system.application.domain.PlatformConfigEntity;
import nan.produced.prism.core.system.application.domain.PlatformConfigType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlatformConfigRepositoryJpa extends JpaRepository<PlatformConfigEntity, UUID> {

    Optional<PlatformConfigEntity> findByConfigTypeAndConfigKeyAndEnabledTrue(PlatformConfigType configType, String configKey);
}

