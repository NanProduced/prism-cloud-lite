package nan.produced.prism.core.device.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.device.application.mapper.DeviceTagMapper;
import nan.produced.prism.core.device.application.port.inbound.DeviceSearchUseCase;
import nan.produced.prism.core.device.application.mapper.DeviceListMapper;
import nan.produced.prism.core.device.application.port.outbound.DeviceCustomFieldDefRepository;
import nan.produced.prism.core.device.application.port.outbound.DeviceCustomFieldValueRepository;
import nan.produced.prism.core.device.application.port.outbound.DeviceRepository;
import nan.produced.prism.core.device.application.port.outbound.DeviceTagRepository;
import nan.produced.prism.core.device.domain.DeviceEntity;
import nan.produced.prism.core.device.domain.customfield.CustomFieldType;
import nan.produced.prism.core.device.domain.customfield.DeviceCustomFieldDefEntity;
import nan.produced.prism.core.device.domain.customfield.DeviceCustomFieldValueEntity;
import nan.produced.prism.core.device.domain.dto.DeviceListVO;
import nan.produced.prism.core.device.domain.dto.TagVO;
import nan.produced.prism.core.device.domain.tags.DeviceTagMapEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeviceSearchApplicationService implements DeviceSearchUseCase {

    private final DeviceRepository deviceRepository;
    private final DeviceTagRepository deviceTagRepository;
    private final DeviceCustomFieldDefRepository customFieldDefRepository;
    private final DeviceCustomFieldValueRepository customFieldValueRepository;
    private final DeviceListMapper deviceListMapper;
    private final DeviceTagMapper deviceTagMapper;

    @Override
    public List<DeviceListVO> listAllUsersDevices(UUID userId) {
        List<DeviceEntity> devices = deviceRepository.findByUserId(userId);
        if (devices == null || devices.isEmpty()) {
            return List.of();
        }

        List<Long> deviceIds = devices.stream().map(DeviceEntity::getDeviceId).toList();

        Map<Long, List<TagVO>> tagsByDevice = loadTags(userId, deviceIds);
        Map<Long, Map<String, Object>> customFieldValuesByDevice = loadCustomFieldValues(userId, deviceIds);

        return devices.stream()
                .map(device -> {
                    DeviceListVO vo = deviceListMapper.toListVO(device);
                    vo.setTags(tagsByDevice.getOrDefault(device.getDeviceId(), List.of()));
                    vo.setCustomFieldValues(customFieldValuesByDevice.getOrDefault(device.getDeviceId(), Map.of()));
                    return vo;
                })
                .toList();
    }

    private Map<Long, List<TagVO>> loadTags(UUID userId, List<Long> deviceIds) {
        List<DeviceTagMapEntity> mappings = deviceTagRepository.findTagMappingsByUserIdAndDeviceIds(userId, deviceIds);
        if (mappings == null || mappings.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<TagVO>> result = new HashMap<>();
        for (DeviceTagMapEntity mapping : mappings) {
            if (mapping == null || mapping.getDeviceId() == null || mapping.getTag() == null) {
                continue;
            }
            TagVO tag = deviceTagMapper.toVO(mapping.getTag());
            result.computeIfAbsent(mapping.getDeviceId(), ignored -> new ArrayList<>()).add(tag);
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
            if (def == null || def.getFieldId() == null) {
                continue;
            }
            defMap.put(def.getFieldId(), def);
        }

        List<DeviceCustomFieldValueEntity> values = customFieldValueRepository.findByUserIdAndDeviceIdIn(userId, deviceIds);
        if (values == null || values.isEmpty()) {
            return Map.of();
        }

        Map<Long, Map<String, Object>> result = new HashMap<>();
        for (DeviceCustomFieldValueEntity valueEntity : values) {
            if (valueEntity == null || valueEntity.getDeviceId() == null || valueEntity.getFieldId() == null) {
                continue;
            }
            DeviceCustomFieldDefEntity def = defMap.get(valueEntity.getFieldId());
            if (def == null || def.getFieldKey() == null || def.getFieldType() == null) {
                continue;
            }

            Object value = extractValue(valueEntity, def.getFieldType());
            result.computeIfAbsent(valueEntity.getDeviceId(), ignored -> new HashMap<>())
                    .put(def.getFieldKey(), value);
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
}
