package nan.produced.prism.auth.domain.apikey.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.auth.domain.apikey.ApiKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, String> {

    List<ApiKeyEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<ApiKeyEntity> findByIdAndUserId(String id, UUID userId);
}

