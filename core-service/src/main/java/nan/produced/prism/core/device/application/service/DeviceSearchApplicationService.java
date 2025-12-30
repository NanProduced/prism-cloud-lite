package nan.produced.prism.core.device.application.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.device.api.dto.DeviceDetailResp;
import nan.produced.prism.core.device.api.dto.FilterDeviceReq;
import nan.produced.prism.core.device.application.converter.DeviceDetailConverter;
import nan.produced.prism.core.device.application.converter.DeviceTagConverter;
import nan.produced.prism.core.device.application.port.inbound.DeviceSearchUseCase;
import nan.produced.prism.core.device.application.converter.DeviceListConverter;
import nan.produced.prism.core.device.application.port.outbound.DeviceCustomFieldDefRepository;
import nan.produced.prism.core.device.application.port.outbound.DeviceCustomFieldValueRepository;
import nan.produced.prism.core.device.application.port.outbound.DeviceRepository;
import nan.produced.prism.core.device.application.port.outbound.DeviceScreenshotRepository;
import nan.produced.prism.core.device.application.port.outbound.DeviceTagRepository;
import nan.produced.prism.core.device.domain.DeviceEntity;
import nan.produced.prism.core.device.domain.customfield.CustomFieldType;
import nan.produced.prism.core.device.domain.customfield.DeviceCustomFieldDefEntity;
import nan.produced.prism.core.device.domain.customfield.DeviceCustomFieldValueEntity;
import nan.produced.prism.core.device.domain.dto.DeviceListVO;
import nan.produced.prism.core.device.domain.dto.TagVO;
import nan.produced.prism.core.device.domain.tags.DeviceTagMapEntity;
import nan.produced.prism.core.device.domain.screenshot.DeviceScreenshotEntity;
import nan.produced.prism.core.media.application.port.outbound.MediaObjectUrlPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeviceSearchApplicationService implements DeviceSearchUseCase {

    private final DeviceRepository deviceRepository;
    private final DeviceTagRepository deviceTagRepository;
    private final DeviceCustomFieldDefRepository customFieldDefRepository;
    private final DeviceCustomFieldValueRepository customFieldValueRepository;
    private final DeviceListConverter deviceListConverter;
    private final DeviceDetailConverter deviceDetailConverter;
    private final DeviceTagConverter deviceTagConverter;
    private final DeviceScreenshotRepository deviceScreenshotRepository;
    private final MediaObjectUrlPort mediaObjectUrlPort;

    @Override
    @Transactional(readOnly = true)
    public List<DeviceListVO> listAllUsersDevices(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        return assembleDeviceList(userId, deviceRepository.findByUserId(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public DeviceDetailResp getDeviceDetail(UUID userId, Long deviceId) {
        if (userId == null || deviceId == null) {
            throw new BizException(ErrorCode.DEVICE_NOT_FOUND_IN_CORE);
        }

        DeviceEntity deviceEntity = deviceRepository.findByDeviceIdAndUserId(deviceId, userId);
        if (deviceEntity == null) {
            throw new BizException(ErrorCode.DEVICE_NOT_FOUND_IN_CORE);
        }

        DeviceDetailResp resp = deviceDetailConverter.toDetailResp(deviceEntity);
        resp.setLastScreenshotUrl(loadLastScreenshotUrl(deviceId));
        resp.setLastScreenshotUploadedAt(loadLastScreenshotUploadedAt(deviceId));

        // tags
        List<TagVO> tagVOS = deviceTagRepository.findByDeviceId(deviceId, userId).stream()
                .map(deviceTagConverter::toVO)
                .toList();
        resp.setTags(tagVOS);

        // customFieldValues（key=fieldKey）
        Map<Long, Map<String, Object>> valuesByDevice = loadCustomFieldValues(userId, List.of(deviceId));
        resp.setCustomFieldValues(valuesByDevice.getOrDefault(deviceId, Map.of()));

        return resp;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceListVO> filterDevices(UUID userId, FilterDeviceReq req) {
        if (userId == null) {
            return List.of();
        }

        List<DeviceEntity> devices = deviceRepository.findByUserId(userId);
        if (devices == null || devices.isEmpty()) {
            return List.of();
        }

        if (req == null || isBlankFilters(req)) {
            return assembleDeviceList(userId, devices);
        }

        String keyword = normalize(req.getKeyword());
        String model = normalize(req.getModel());
        Integer networkType = req.getNetworkType();
        Integer onlineStatus = req.getOnlineStatus();

        List<DeviceEntity> filtered = devices.stream()
                .filter(device -> keyword == null || matchKeyword(device, keyword))
                .filter(device -> model == null || containsIgnoreCase(device.getModel(), model))
                .filter(device -> networkType == null || Objects.equals(device.getNetworkType(), networkType))
                .filter(device -> onlineStatus == null || Objects.equals(device.getOnlineStatus(), onlineStatus))
                .toList();

        return assembleDeviceList(userId, filtered);
    }

    private List<DeviceListVO> assembleDeviceList(UUID userId, List<DeviceEntity> devices) {
        if (devices == null || devices.isEmpty()) {
            return List.of();
        }

        List<Long> deviceIds = devices.stream()
                .map(DeviceEntity::getDeviceId)
                .filter(Objects::nonNull)
                .toList();
        if (deviceIds.isEmpty()) {
            return List.of();
        }

        Map<Long, List<TagVO>> tagsByDevice = loadTags(userId, deviceIds);
        Map<Long, Map<String, Object>> customFieldValuesByDevice = loadCustomFieldValues(userId, deviceIds);
        Map<Long, DeviceScreenshotEntity> latestScreenshotByDevice = loadLatestScreenshots(deviceIds);
        Map<Long, String> lastScreenshotUrlByDevice = toScreenshotUrlMap(latestScreenshotByDevice);
        Map<Long, Instant> lastScreenshotUploadedAtByDevice = toScreenshotUploadedAtMap(latestScreenshotByDevice);

        return devices.stream()
                .map(device -> {
                    DeviceListVO vo = deviceListConverter.toListVO(device);
                    vo.setTags(tagsByDevice.getOrDefault(device.getDeviceId(), List.of()));
                    vo.setCustomFieldValues(customFieldValuesByDevice.getOrDefault(device.getDeviceId(), Map.of()));
                    vo.setLastScreenshotUrl(lastScreenshotUrlByDevice.get(device.getDeviceId()));
                    vo.setLastScreenshotUploadedAt(lastScreenshotUploadedAtByDevice.get(device.getDeviceId()));
                    return vo;
                })
                .toList();
    }

    private Map<Long, DeviceScreenshotEntity> loadLatestScreenshots(List<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Map.of();
        }

        List<DeviceScreenshotEntity> latest = deviceScreenshotRepository.findLatestByDeviceIds(deviceIds);
        if (latest == null || latest.isEmpty()) {
            return Map.of();
        }

        Map<Long, DeviceScreenshotEntity> result = new HashMap<>();
        for (DeviceScreenshotEntity entity : latest) {
            if (entity == null || entity.getDeviceId() == null || !StringUtils.hasText(entity.getS3Key())) {
                continue;
            }
            result.put(entity.getDeviceId(), entity);
        }
        return result;
    }

    private Map<Long, String> toScreenshotUrlMap(Map<Long, DeviceScreenshotEntity> latestByDevice) {
        if (latestByDevice == null || latestByDevice.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        for (Map.Entry<Long, DeviceScreenshotEntity> entry : latestByDevice.entrySet()) {
            Long deviceId = entry.getKey();
            DeviceScreenshotEntity entity = entry.getValue();
            if (deviceId == null || entity == null || !StringUtils.hasText(entity.getS3Key())) {
                continue;
            }
            result.put(deviceId, mediaObjectUrlPort.toPublicUrl(entity.getS3Key()));
        }
        return result;
    }

    private Map<Long, Instant> toScreenshotUploadedAtMap(Map<Long, DeviceScreenshotEntity> latestByDevice) {
        if (latestByDevice == null || latestByDevice.isEmpty()) {
            return Map.of();
        }
        Map<Long, Instant> result = new HashMap<>();
        for (Map.Entry<Long, DeviceScreenshotEntity> entry : latestByDevice.entrySet()) {
            Long deviceId = entry.getKey();
            DeviceScreenshotEntity entity = entry.getValue();
            if (deviceId == null || entity == null) {
                continue;
            }
            if (entity.getUploadedAt() != null) {
                result.put(deviceId, entity.getUploadedAt());
            }
        }
        return result;
    }

    private String loadLastScreenshotUrl(Long deviceId) {
        if (deviceId == null) {
            return null;
        }
        return deviceScreenshotRepository.findLatestByDeviceId(deviceId)
                .map(DeviceScreenshotEntity::getS3Key)
                .filter(StringUtils::hasText)
                .map(mediaObjectUrlPort::toPublicUrl)
                .orElse(null);
    }

    private Instant loadLastScreenshotUploadedAt(Long deviceId) {
        if (deviceId == null) {
            return null;
        }
        return deviceScreenshotRepository.findLatestByDeviceId(deviceId)
                .map(DeviceScreenshotEntity::getUploadedAt)
                .orElse(null);
    }

    private Map<Long, List<TagVO>> loadTags(UUID userId, List<Long> deviceIds) {
        List<DeviceTagMapEntity> mappings = deviceTagRepository.findTagMappingsByUserIdAndDeviceIds(userId, deviceIds);
        if (mappings == null || mappings.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<TagVO>> result = new HashMap<>();
        for (DeviceTagMapEntity mapping : mappings) {
            if (mapping != null && mapping.getDeviceId() != null && mapping.getTag() != null) {
                TagVO tag = deviceTagConverter.toVO(mapping.getTag());
                result.computeIfAbsent(mapping.getDeviceId(), ignored -> new ArrayList<>()).add(tag);
            }
        }
        return result;
    }

    private Map<Long, Map<String, Object>> loadCustomFieldValues(UUID userId, List<Long> deviceIds) {
        List<DeviceCustomFieldDefEntity> defs = customFieldDefRepository.findByUserId(userId);
        if (defs == null || defs.isEmpty()) {
            return Map.of();
        }

        Map<Long, DeviceCustomFieldDefEntity> defMap = new HashMap<>();
        for (DeviceCustomFieldDefEntity def : defs) {
            if (def != null && def.getFieldId() != null) {
                defMap.put(def.getFieldId(), def);
            }
        }

        List<DeviceCustomFieldValueEntity> values = customFieldValueRepository.findByUserIdAndDeviceIdIn(userId, deviceIds);
        if (values == null || values.isEmpty()) {
            return Map.of();
        }

        Map<Long, Map<String, Object>> result = new HashMap<>();
        for (DeviceCustomFieldValueEntity valueEntity : values) {
            if (valueEntity != null && valueEntity.getDeviceId() != null && valueEntity.getFieldId() != null) {
                DeviceCustomFieldDefEntity def = defMap.get(valueEntity.getFieldId());
                if (def != null && def.getFieldKey() != null && def.getFieldType() != null) {
                    Object value = extractValue(valueEntity, def.getFieldType());
                    result.computeIfAbsent(valueEntity.getDeviceId(), ignored -> new HashMap<>())
                            .put(def.getFieldKey(), value);
                }
            }
        }
        return result;
    }

    private Object extractValue(DeviceCustomFieldValueEntity entity, CustomFieldType type) {
        return switch (type) {
            case NUMBER -> entity.getValueNumber();
            case DATETIME -> entity.getValueDateTime() != null ? entity.getValueDateTime().toString() : null;
            case BOOLEAN -> entity.getValueBoolean();
            case MULTI_SELECT -> entity.getValueMultiText();
            default -> entity.getValueText();
        };
    }

    private boolean isBlankFilters(FilterDeviceReq req) {
        return !StringUtils.hasText(req.getKeyword())
                && !StringUtils.hasText(req.getModel())
                && req.getNetworkType() == null
                && req.getOnlineStatus() == null;
    }

    private boolean matchKeyword(DeviceEntity device, String keyword) {
        if (device == null) {
            return false;
        }

        return containsIgnoreCase(device.getDeviceName(), keyword)
                || containsIgnoreCase(device.getDescription(), keyword);
    }

    private static String normalize(String input) {
        if (!StringUtils.hasText(input)) {
            return null;
        }
        return input.trim();
    }

    private static boolean containsIgnoreCase(String text, String keyword) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(keyword)) {
            return false;
        }
        return text.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }
}
