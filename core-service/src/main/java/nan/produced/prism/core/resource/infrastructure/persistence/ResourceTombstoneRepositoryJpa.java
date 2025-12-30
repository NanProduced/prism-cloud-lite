package nan.produced.prism.core.resource.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import nan.produced.prism.core.resource.domain.ResourceTombstoneEntity;
import nan.produced.prism.core.resource.domain.ResourceTombstoneKey;
import nan.produced.prism.core.resource.domain.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceTombstoneRepositoryJpa extends JpaRepository<ResourceTombstoneEntity, ResourceTombstoneKey> {

    List<ResourceTombstoneEntity> findByIdUserIdAndIdResourceTypeAndIdRefIdIn(
            UUID userId, ResourceType resourceType, Collection<String> refIds);

    @Modifying
    @Query("delete from ResourceTombstoneEntity t where t.purgeAt < :now")
    int deleteExpired(@Param("now") OffsetDateTime now);
}

