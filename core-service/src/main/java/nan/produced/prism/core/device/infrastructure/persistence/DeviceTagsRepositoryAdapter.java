package nan.produced.prism.core.device.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.device.application.port.outbound.DeviceTagRepository;
import nan.produced.prism.core.device.domain.tags.DeviceTagEntity;
import nan.produced.prism.core.device.domain.tags.DeviceTagMapEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 设备标签数据仓库适配器
 * <p>
 * 实现 {@link DeviceTagRepository} 出站端口
 * 作为六边形架构中的适配器，负责将域模型的仓库接口与底层的持久化实现（Spring Data JPA）相连接
 *
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceTagsRepositoryAdapter implements DeviceTagRepository {

    private final DeviceTagRepositoryJpa deviceTagRepositoryJpa;
    private final DeviceTagMapRepositoryJpa deviceTagMapRepositoryJpa;

    @Override
    public DeviceTagEntity save(DeviceTagEntity tag) {
        return deviceTagRepositoryJpa.save(tag);
    }

    @Override
    public Optional<DeviceTagEntity> findByUserIdAndSlug(UUID userId, String slug) {
        if (userId == null || slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return deviceTagRepositoryJpa.findByUserIdAndSlug(userId, slug);
    }

    @Override
    public boolean existsByUserIdAndSlug(UUID userId, String slug) {
        if (userId == null || slug == null || slug.isBlank()) {
            return false;
        }
        return deviceTagRepositoryJpa.existsByUserIdAndSlug(userId, slug);
    }

    @Override
    public List<DeviceTagEntity> findByUserId(UUID userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return deviceTagRepositoryJpa.findByUserIdOrderByCreateTimeDesc(userId);
    }

    @Override
    public Optional<DeviceTagEntity> findByTagIdAndUserId(Long tagId, UUID userId) {
        if (tagId == null || userId == null) {
            return Optional.empty();
        }
        return deviceTagRepositoryJpa.findByTagIdAndUserId(tagId, userId);
    }

    @Override
    public List<DeviceTagEntity> findByUserIdAndSlugIn(UUID userId, List<String> slugs) {
        if (userId == null || slugs == null || slugs.isEmpty()) {
            return Collections.emptyList();
        }
        return deviceTagRepositoryJpa.findByUserIdAndSlugIn(userId, slugs);
    }

    @Override
    @Transactional
    public void deleteByUserIdAndSlug(UUID userId, String slug) {
        if (userId == null || slug == null || slug.isBlank()) {
            log.warn("DeviceTagsRepositoryAdapter - 删除标签时参数为空: userId={}, slug={}", userId, slug);
            return;
        }
        deviceTagRepositoryJpa.deleteByUserIdAndSlug(userId, slug);
    }

    @Override
    public void delete(DeviceTagEntity tag) {
        if (tag == null) {
            return;
        }
        deviceTagRepositoryJpa.delete(tag);
    }

    @Override
    public List<DeviceTagEntity> findByDeviceId(Long deviceId, UUID userId) {
        if (deviceId == null || userId == null) {
            return Collections.emptyList();
        }
        return deviceTagMapRepositoryJpa.findByDeviceIdAndUserIdWithTag(deviceId, userId)
                .stream()
                .map(DeviceTagMapEntity::getTag)
                .toList();
    }

    @Override
    @Transactional
    public void replaceDeviceTags(Long deviceId, UUID userId, List<Long> tagIds) {
        if (deviceId == null || userId == null) {
            log.warn("DeviceTagsRepositoryAdapter - 替换设备标签时参数为空: deviceId={}, userId={}", deviceId, userId);
            return;
        }

        // 1. 删除现有映射
        deviceTagMapRepositoryJpa.deleteByDeviceId(deviceId);

        // 2. 如果有新标签，创建映射
        if (tagIds != null && !tagIds.isEmpty()) {
            var now = LocalDateTime.now();
            var mappings = tagIds.stream()
                    .map(tagId -> {
                        var mapping = new DeviceTagMapEntity();
                        mapping.setDeviceId(deviceId);
                        mapping.setTagId(tagId);
                        mapping.setUserId(userId);
                        mapping.setAssignedTime(now);
                        return mapping;
                    })
                    .toList();
            deviceTagMapRepositoryJpa.saveAll(mappings);
        }

        log.debug("DeviceTagsRepositoryAdapter - 替换设备标签: deviceId={}, tagCount={}",
                deviceId, tagIds != null ? tagIds.size() : 0);
    }

    @Override
    @Transactional
    public void deleteTagMappings(Long tagId) {
        if (tagId == null) {
            return;
        }
        deviceTagMapRepositoryJpa.deleteByTagId(tagId);
    }

    @Override
    public boolean hasDeviceMappings(Long tagId) {
        if (tagId == null) {
            return false;
        }
        return deviceTagMapRepositoryJpa.existsByTagId(tagId);
    }
}
