package nan.produced.prism.core.resource.application.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.resource.domain.ResourceTombstoneEntity;
import nan.produced.prism.core.resource.domain.ResourceTombstoneKey;
import nan.produced.prism.core.resource.domain.ResourceType;
import nan.produced.prism.core.resource.infrastructure.persistence.ResourceTombstoneRepositoryJpa;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceTombstoneService {

    // Must match telemetry retention (currently 60 days).
    public static final int DEFAULT_RETENTION_DAYS = 60;
    public static final int NO_VERSION = 0;

    private final ResourceTombstoneRepositoryJpa repository;

    @Transactional
    public void markDeleted(UUID userId, ResourceType type, String refId, Integer refVersion, String displayName) {
        if (userId == null || type == null || !StringUtils.hasText(refId)) {
            return;
        }

        int version = refVersion != null ? Math.max(0, refVersion) : NO_VERSION;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        ResourceTombstoneKey key = new ResourceTombstoneKey(userId, type, refId.trim(), version);
        ResourceTombstoneEntity entity = repository.findById(key).orElse(null);

        if (entity == null) {
            entity = new ResourceTombstoneEntity();
            entity.setId(key);
            entity.setCreatedAt(now);
        }

        entity.setDisplayName(StringUtils.hasText(displayName) ? displayName.trim() : null);
        entity.setDeletedAt(now);
        entity.setPurgeAt(now.plusDays(DEFAULT_RETENTION_DAYS));
        entity.setUpdatedAt(now);

        try {
            repository.save(entity);
        } catch (Exception ex) {
            log.warn("ResourceTombstone - save failed (ignored): userId={}, type={}, refId={}, ver={}",
                    userId, type, refId, version, ex);
        }
    }

    @Transactional(readOnly = true)
    public Map<ResourceTombstoneKey, ResourceTombstoneEntity> findByUserAndTypeAndRefIds(
            UUID userId, ResourceType type, Collection<String> refIds) {

        if (userId == null || type == null || refIds == null || refIds.isEmpty()) {
            return Map.of();
        }

        var list = repository.findByIdUserIdAndIdResourceTypeAndIdRefIdIn(userId, type, refIds);
        if (list == null || list.isEmpty()) {
            return Map.of();
        }

        Map<ResourceTombstoneKey, ResourceTombstoneEntity> map = new HashMap<>();
        for (var t : list) {
            if (t == null || t.getId() == null) {
                continue;
            }
            map.put(t.getId(), t);
        }
        return map;
    }

    @Transactional
    public int cleanupExpired() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return repository.deleteExpired(now);
    }
}

