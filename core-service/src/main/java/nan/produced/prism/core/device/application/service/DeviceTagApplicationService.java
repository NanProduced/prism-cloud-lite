package nan.produced.prism.core.device.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.util.TextSlugifier;
import nan.produced.prism.core.device.application.converter.DeviceTagConverter;
import nan.produced.prism.core.device.application.port.inbound.DeviceTagUseCase;
import nan.produced.prism.core.device.application.port.outbound.DeviceTagRepository;
import nan.produced.prism.core.device.domain.dto.TagVO;
import nan.produced.prism.core.device.domain.tags.DeviceTagEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 设备标签应用服务
 * <p>
 * 实现 {@link DeviceTagUseCase} 用例接口
 *
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTagApplicationService implements DeviceTagUseCase {

    private final DeviceTagRepository deviceTagRepository;

    private final DeviceTagConverter deviceTagConverter;

    @Override
    @Transactional
    public TagVO createTag(UUID userId, String tagName, String color, String icon, String description) {
        String slug = TextSlugifier.toSlug(tagName);

        // 检查 slug 是否已存在
        if (deviceTagRepository.existsByUserIdAndSlug(userId, slug)) {
            throw new BizException(ErrorCode.DEVICE_TAG_SLUG_ALREADY_EXISTS);
        }

        var now = LocalDateTime.now();
        var tag = deviceTagConverter.toNewEntity(
                userId,
                System.currentTimeMillis(),
                tagName,
                slug,
                color,
                icon,
                description,
                now);

        var saved = deviceTagRepository.save(tag);
        log.debug("DeviceTagApplicationService - 创建标签成功: userId={}, slug={}", userId, slug);
        return deviceTagConverter.toVO(saved);
    }

    @Override
    @Transactional
    public TagVO updateTag(UUID userId, String slug, String tagName, String color, String icon, String description) {
        var tag = deviceTagRepository.findByUserIdAndSlug(userId, slug)
                .orElseThrow(() -> new BizException(ErrorCode.DEVICE_TAG_NOT_FOUND));

        // 如果修改了标签名称，需要检查新 slug 是否冲突
        String newSlug = TextSlugifier.toSlug(tagName);
        if (!newSlug.equals(tag.getSlug())) {
            if (deviceTagRepository.existsByUserIdAndSlug(userId, newSlug)) {
                throw new BizException(ErrorCode.DEVICE_TAG_SLUG_ALREADY_EXISTS);
            }
            tag.setSlug(newSlug);
        }

        deviceTagConverter.applyUpdate(tag, tagName, color, icon, description, LocalDateTime.now());

        var saved = deviceTagRepository.save(tag);
        log.info("DeviceTagApplicationService - 更新标签成功: userId={}, oldSlug={}, newSlug={}",
                userId, slug, saved.getSlug());
        return deviceTagConverter.toVO(saved);
    }

    @Override
    @Transactional
    public void deleteTag(UUID userId, String slug) {
        var tag = deviceTagRepository.findByUserIdAndSlug(userId, slug)
                .orElseThrow(() -> new BizException(ErrorCode.DEVICE_TAG_NOT_FOUND));

        // 先删除标签与设备的关联关系
        deviceTagRepository.deleteTagMappings(tag.getTagId());

        // 再删除标签
        deviceTagRepository.delete(tag);
        log.info("DeviceTagApplicationService - 删除标签成功: userId={}, slug={}", userId, slug);
    }

    @Override
    public TagVO getTagBySlug(UUID userId, String slug) {
        return deviceTagRepository.findByUserIdAndSlug(userId, slug)
                .map(deviceTagConverter::toVO)
                .orElseThrow(() -> new BizException(ErrorCode.DEVICE_TAG_NOT_FOUND));
    }

    @Override
    public List<TagVO> getUserTags(UUID userId) {
        return deviceTagRepository.findByUserId(userId).stream()
                .map(deviceTagConverter::toVO)
                .toList();
    }

    @Override
    public List<TagVO> getDeviceTags(UUID userId, Long deviceId) {
        return deviceTagRepository.findByDeviceId(deviceId, userId).stream()
                .map(deviceTagConverter::toVO)
                .toList();
    }

    @Override
    @Transactional
    public void linkTags(Long deviceId, UUID userId, List<String> slugs) {
        List<Long> tagIds = List.of();

        if (slugs != null && !slugs.isEmpty()) {
            // 根据 slug 列表查找标签
            var tags = deviceTagRepository.findByUserIdAndSlugIn(userId, slugs);

            // 检查是否所有 slug 都找到了对应的标签
            if (tags.size() != slugs.size()) {
                var foundSlugs = tags.stream().map(DeviceTagEntity::getSlug).toList();
                var missingSlugs = slugs.stream()
                        .filter(s -> !foundSlugs.contains(s))
                        .toList();
                log.warn("DeviceTagApplicationService - 部分标签不存在: deviceId={}, missingSlugs={}",
                        deviceId, missingSlugs);
                throw new BizException(ErrorCode.DEVICE_TAG_NOT_FOUND);
            }

            tagIds = tags.stream().map(DeviceTagEntity::getTagId).toList();
        }

        deviceTagRepository.replaceDeviceTags(deviceId, userId, tagIds);
        log.info("DeviceTagApplicationService - 关联标签成功: deviceId={}, tagCount={}", deviceId, tagIds.size());
    }
}
