package nan.produced.prism.core.device.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.core.device.domain.customfield.DeviceCustomFieldDefEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceCustomFieldDefRepositoryJpa extends JpaRepository<DeviceCustomFieldDefEntity, Long> {

    @EntityGraph(attributePaths = "options")
    List<DeviceCustomFieldDefEntity> findByUserId(UUID userId);

    @EntityGraph(attributePaths = "options")
    List<DeviceCustomFieldDefEntity> findByUserIdAndFieldIdIn(UUID userId, List<Long> fieldIds);

    @EntityGraph(attributePaths = "options")
    Optional<DeviceCustomFieldDefEntity> findByFieldIdAndUserId(Long fieldId, UUID userId);

    boolean existsByUserIdAndFieldKey(UUID userId, String fieldKey);
}

