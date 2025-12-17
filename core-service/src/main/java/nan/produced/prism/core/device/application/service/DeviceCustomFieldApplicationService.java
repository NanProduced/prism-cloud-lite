package nan.produced.prism.core.device.application.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.util.IdGenerator;
import nan.produced.prism.core.common.util.TextSlugifier;
import nan.produced.prism.core.device.application.converter.DeviceCustomFieldConverter;
import nan.produced.prism.core.device.application.port.inbound.DeviceCustomFieldOptionSpec;
import nan.produced.prism.core.device.application.port.inbound.DeviceCustomFieldUseCase;
import nan.produced.prism.core.device.application.port.outbound.DeviceCustomFieldDefRepository;
import nan.produced.prism.core.device.application.port.outbound.DeviceCustomFieldValueRepository;
import nan.produced.prism.core.device.application.port.outbound.DeviceRepository;
import nan.produced.prism.core.device.domain.customfield.CustomFieldType;
import nan.produced.prism.core.device.domain.customfield.DeviceCustomFieldDefEntity;
import nan.produced.prism.core.device.domain.customfield.DeviceCustomFieldOptionEntity;
import nan.produced.prism.core.device.domain.customfield.DeviceCustomFieldValueEntity;
import nan.produced.prism.core.device.domain.dto.DeviceCustomFieldDefVO;
import nan.produced.prism.core.device.domain.dto.DeviceCustomFieldOptionVO;
import nan.produced.prism.core.user.api.UserQuotaFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import static nan.produced.prism.core.device.domain.customfield.CustomFieldConstant.DEF_SORT;
import static nan.produced.prism.core.device.domain.customfield.CustomFieldConstant.OPTION_SORT;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceCustomFieldApplicationService implements DeviceCustomFieldUseCase {

    private static final String TIER_PRO = "PRO";

    private final DeviceCustomFieldDefRepository customFieldDefRepository;
    private final DeviceCustomFieldValueRepository customFieldValueRepository;
    private final DeviceRepository deviceRepository;
    private final UserQuotaFacade userQuotaFacade;
    private final DeviceCustomFieldConverter deviceCustomFieldConverter;

    @Override
    @Transactional(readOnly = true)
    public List<DeviceCustomFieldDefVO> listCustomFieldDefs(UUID userId) {
        List<DeviceCustomFieldDefEntity> defs = customFieldDefRepository.findByUserId(userId);
        defs.sort(DEF_SORT);
        return defs.stream().map(this::toDefVO).toList();
    }

    @Override
    @Transactional
    public DeviceCustomFieldDefVO createCustomFieldDef(
            UUID userId,
            String tier,
            String displayName,
            CustomFieldType fieldType,
            String fieldKey,
            String description,
            String icon,
            Boolean planTierRequired,
            Integer sequence,
            List<DeviceCustomFieldOptionSpec> options) {

        if (!StringUtils.hasText(displayName) || fieldType == null) {
            throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_VALUE_INVALID);
        }

        boolean isPro = isProTier(tier);
        boolean normalizedPlanTierRequired = Boolean.TRUE.equals(planTierRequired);
        if (normalizedPlanTierRequired && !isPro) {
            throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_PRO_REQUIRED);
        }

        validateOptions(fieldType, options);

        String slugBase = StringUtils.hasText(fieldKey) ? fieldKey : displayName;
        String baseKey = TextSlugifier.toSlug(slugBase);
        if (!StringUtils.hasText(baseKey)) {
            throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_VALUE_INVALID, "fieldKey is blank after slugify");
        }

        String uniqueKey = ensureUniqueFieldKey(userId, baseKey);

        // 先扣减额度，再创建字段（同一事务回滚可恢复额度）
        userQuotaFacade.consumeCustomColumns(userId, tier, 1);

        OffsetDateTime now = OffsetDateTime.now();
        DeviceCustomFieldDefEntity def = new DeviceCustomFieldDefEntity();
        def.setFieldId(IdGenerator.nextId());
        def.setUserId(userId);
        def.setFieldKey(uniqueKey);
        def.setFieldType(fieldType);
        def.setDisplayName(displayName);
        def.setDescription(description);
        def.setIcon(icon);
        def.setPlanTierRequired(normalizedPlanTierRequired);
        def.setSequence(sequence);
        def.setCreateTime(now);
        def.setUpdateTime(now);

        if (options != null && !options.isEmpty()) {
            def.getOptions().clear();
            def.getOptions().addAll(buildOptionEntities(def, options, now));
        }

        DeviceCustomFieldDefEntity saved = customFieldDefRepository.save(def);
        log.debug("Created custom field: userId={}, fieldId={}, fieldKey={}, type={}",
                userId, saved.getFieldId(), saved.getFieldKey(), saved.getFieldType());
        return toDefVO(saved);
    }

    @Override
    @Transactional
    public DeviceCustomFieldDefVO updateCustomFieldDef(
            UUID userId,
            String tier,
            Long fieldId,
            String displayName,
            String description,
            String icon,
            Boolean planTierRequired,
            Integer sequence,
            List<DeviceCustomFieldOptionSpec> options) {

        DeviceCustomFieldDefEntity def = customFieldDefRepository.findByFieldIdAndUserId(fieldId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_DEF_NOT_FOUND));

        boolean isPro = isProTier(tier);
        if (Boolean.TRUE.equals(def.getPlanTierRequired()) && !isPro) {
            throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_PRO_REQUIRED);
        }

        if (StringUtils.hasText(displayName)) {
            def.setDisplayName(displayName);
        }
        if (description != null) {
            def.setDescription(description);
        }
        if (icon != null) {
            def.setIcon(icon);
        }
        if (sequence != null) {
            def.setSequence(sequence);
        }

        if (planTierRequired != null) {
            if (planTierRequired && !isPro) {
                throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_PRO_REQUIRED);
            }
            def.setPlanTierRequired(planTierRequired);
        }

        if (options != null) {
            validateOptions(def.getFieldType(), options);
            OffsetDateTime now = OffsetDateTime.now();
            def.getOptions().clear();
            def.getOptions().addAll(buildOptionEntities(def, options, now));
        }

        def.setUpdateTime(OffsetDateTime.now());
        DeviceCustomFieldDefEntity saved = customFieldDefRepository.save(def);
        return toDefVO(saved);
    }

    @Override
    @Transactional
    public void deleteCustomFieldDef(UUID userId, String tier, Long fieldId) {
        DeviceCustomFieldDefEntity def = customFieldDefRepository.findByFieldIdAndUserId(fieldId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_DEF_NOT_FOUND));

        if (Boolean.TRUE.equals(def.getPlanTierRequired()) && !isProTier(tier)) {
            throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_PRO_REQUIRED);
        }

        customFieldValueRepository.deleteByUserIdAndFieldId(userId, fieldId);
        customFieldDefRepository.delete(def);
        userQuotaFacade.releaseCustomColumns(userId, 1);

        log.debug("Deleted custom field: userId={}, fieldId={}", userId, fieldId);
    }

    @Override
    @Transactional
    public Map<String, Object> patchDeviceCustomFieldValues(UUID userId, String tier, Long deviceId, Map<Long, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }

        UUID ownerId = deviceRepository.findUserIdByDeviceId(deviceId);
        if (ownerId == null || !ownerId.equals(userId)) {
            throw new BizException(ErrorCode.DEVICE_NOT_FOUND_IN_CORE);
        }

        boolean isPro = isProTier(tier);
        List<Long> fieldIds = new ArrayList<>(values.keySet());
        List<DeviceCustomFieldDefEntity> defs = customFieldDefRepository.findByUserIdAndFieldIdIn(userId, fieldIds);
        if (defs.size() != fieldIds.size()) {
            throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_DEF_NOT_FOUND);
        }

        Map<Long, DeviceCustomFieldDefEntity> defMap = new HashMap<>();
        for (DeviceCustomFieldDefEntity def : defs) {
            defMap.put(def.getFieldId(), def);
        }

        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> updatedValues = new HashMap<>();

        for (Map.Entry<Long, Object> entry : values.entrySet()) {
            Long fieldId = entry.getKey();
            Object raw = entry.getValue();
            DeviceCustomFieldDefEntity def = defMap.get(fieldId);
            if (def == null) {
                throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_DEF_NOT_FOUND);
            }
            if (Boolean.TRUE.equals(def.getPlanTierRequired()) && !isPro) {
                throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_PRO_REQUIRED);
            }

            if (raw == null) {
                customFieldValueRepository.deleteByUserIdAndDeviceIdAndFieldId(userId, deviceId, fieldId);
                updatedValues.put(def.getFieldKey(), null);
                continue;
            }

            DeviceCustomFieldValueEntity valueEntity = customFieldValueRepository
                    .findByUserIdAndDeviceIdAndFieldId(userId, deviceId, fieldId)
                    .orElseGet(() -> newValueEntity(userId, deviceId, fieldId, now));

            clearValueColumns(valueEntity);
            applyValue(valueEntity, def, raw);
            valueEntity.setUpdateTime(now);
            DeviceCustomFieldValueEntity saved = customFieldValueRepository.save(valueEntity);
            updatedValues.put(def.getFieldKey(), extractValue(saved, def.getFieldType()));
        }

        return updatedValues;
    }

    private DeviceCustomFieldValueEntity newValueEntity(UUID userId, Long deviceId, Long fieldId, OffsetDateTime now) {
        DeviceCustomFieldValueEntity entity = new DeviceCustomFieldValueEntity();
        entity.setValueId(IdGenerator.nextId());
        entity.setUserId(userId);
        entity.setDeviceId(deviceId);
        entity.setFieldId(fieldId);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        return entity;
    }

    private void clearValueColumns(DeviceCustomFieldValueEntity entity) {
        entity.setValueText(null);
        entity.setValueNumber(null);
        entity.setValueDateTime(null);
        entity.setValueBoolean(null);
        entity.setValueMultiText(null);
        entity.setValueJson(null);
    }

    private void applyValue(DeviceCustomFieldValueEntity entity, DeviceCustomFieldDefEntity def, Object raw) {
        CustomFieldType type = def.getFieldType();
        switch (type) {
            case TEXT, URL, EMAIL, PHONE, COUNTRY -> entity.setValueText(asString(raw));
            case NUMBER -> entity.setValueNumber(asBigDecimal(raw));
            case DATETIME -> entity.setValueDateTime(asOffsetDateTime(raw));
            case BOOLEAN -> entity.setValueBoolean(asBoolean(raw));
            case SELECT -> entity.setValueText(validateSelectValue(def, asString(raw)));
            case MULTI_SELECT -> entity.setValueMultiText(validateMultiSelectValue(def, asStringArray(raw)));
            default -> throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_VALUE_INVALID);
        }
    }

    private String validateSelectValue(DeviceCustomFieldDefEntity def, String value) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_VALUE_INVALID);
        }
        Set<String> activeKeys = getActiveOptionKeys(def);
        if (!activeKeys.contains(value)) {
            throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_OPTION_INVALID);
        }
        return value;
    }

    private String[] validateMultiSelectValue(DeviceCustomFieldDefEntity def, String[] values) {
        if (values == null || values.length == 0) {
            throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_VALUE_INVALID);
        }
        Set<String> activeKeys = getActiveOptionKeys(def);
        for (String v : values) {
            if (!activeKeys.contains(v)) {
                throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_OPTION_INVALID);
            }
        }
        return values;
    }

    private Set<String> getActiveOptionKeys(DeviceCustomFieldDefEntity def) {
        List<DeviceCustomFieldOptionEntity> options = def.getOptions() != null ? def.getOptions() : List.of();
        Set<String> keys = new HashSet<>();
        for (DeviceCustomFieldOptionEntity opt : options) {
            if (Boolean.FALSE.equals(opt.getActive())) {
                continue;
            }
            if (StringUtils.hasText(opt.getOptionKey())) {
                keys.add(opt.getOptionKey());
            }
        }
        if (keys.isEmpty()) {
            throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_OPTION_INVALID);
        }
        return keys;
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

    private void validateOptions(CustomFieldType fieldType, List<DeviceCustomFieldOptionSpec> options) {
        if (fieldType == CustomFieldType.SELECT || fieldType == CustomFieldType.MULTI_SELECT) {
            if (options == null || options.isEmpty()) {
                throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_OPTION_INVALID);
            }
            for (DeviceCustomFieldOptionSpec opt : options) {
                if (opt == null || !StringUtils.hasText(opt.displayName())) {
                    throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_OPTION_INVALID);
                }
            }
            return;
        }

        if (options != null && !options.isEmpty()) {
            throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_OPTION_INVALID);
        }
    }

    private List<DeviceCustomFieldOptionEntity> buildOptionEntities(
            DeviceCustomFieldDefEntity def,
            List<DeviceCustomFieldOptionSpec> options,
            OffsetDateTime now) {

        List<DeviceCustomFieldOptionEntity> result = new ArrayList<>();
        Set<String> usedKeys = new HashSet<>();

        for (DeviceCustomFieldOptionSpec spec : options) {
            if (spec == null) {
                continue;
            }

            String keyBase = StringUtils.hasText(spec.optionKey()) ? spec.optionKey() : spec.displayName();
            String optionKey = TextSlugifier.toSlug(keyBase);
            if (!StringUtils.hasText(optionKey)) {
                throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_OPTION_INVALID);
            }
            if (!usedKeys.add(optionKey)) {
                throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_OPTION_INVALID, "duplicate optionKey: " + optionKey);
            }

            DeviceCustomFieldOptionEntity option = new DeviceCustomFieldOptionEntity();
            option.setOptionId(IdGenerator.nextId());
            option.setField(def);
            option.setOptionKey(optionKey);
            option.setDisplayName(spec.displayName());
            option.setDescription(spec.description());
            option.setSequence(spec.sequence());
            option.setActive(spec.active() == null ? Boolean.TRUE : spec.active());
            option.setColor(spec.color());
            option.setCreateTime(now);
            option.setUpdateTime(now);
            result.add(option);
        }

        result.sort(OPTION_SORT);
        return result;
    }

    private String ensureUniqueFieldKey(UUID userId, String baseKey) {
        if (!customFieldDefRepository.existsByUserIdAndFieldKey(userId, baseKey)) {
            return baseKey;
        }

        for (int i = 2; i <= 50; i++) {
            String candidate = baseKey + "-" + i;
            if (!customFieldDefRepository.existsByUserIdAndFieldKey(userId, candidate)) {
                return candidate;
            }
        }

        throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_KEY_ALREADY_EXISTS);
    }

    private DeviceCustomFieldDefVO toDefVO(DeviceCustomFieldDefEntity entity) {
        DeviceCustomFieldDefVO vo = deviceCustomFieldConverter.toDefVO(entity);

        List<DeviceCustomFieldOptionVO> options = List.of();
        if (entity.getOptions() != null && !entity.getOptions().isEmpty()) {
            List<DeviceCustomFieldOptionEntity> optionEntities = new ArrayList<>(entity.getOptions());
            optionEntities.sort(OPTION_SORT);
            options = optionEntities.stream()
                    .map(deviceCustomFieldConverter::toOptionVO)
                    .toList();
        }

        vo.setOptions(options);
        return vo;
    }

    private boolean isProTier(String tier) {
        return tier != null && TIER_PRO.equalsIgnoreCase(tier.trim());
    }

    private String asString(Object raw) {
        if (raw instanceof String s) {
            if (!StringUtils.hasText(s)) {
                throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_VALUE_INVALID);
            }
            return s;
        }
        if (raw instanceof Number || raw instanceof Boolean) {
            return String.valueOf(raw);
        }
        throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_VALUE_INVALID);
    }

    private BigDecimal asBigDecimal(Object raw) {
        if (raw instanceof BigDecimal bd) {
            return bd;
        }
        if (raw instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        if (raw instanceof String s && StringUtils.hasText(s)) {
            try {
                return new BigDecimal(s.trim());
            } catch (NumberFormatException ex) {
                throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_VALUE_INVALID);
            }
        }
        throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_VALUE_INVALID);
    }

    private OffsetDateTime asOffsetDateTime(Object raw) {
        if (raw instanceof OffsetDateTime odt) {
            return odt;
        }
        if (raw instanceof String s && StringUtils.hasText(s)) {
            try {
                return OffsetDateTime.parse(s.trim());
            } catch (Exception ex) {
                throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_VALUE_INVALID);
            }
        }
        throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_VALUE_INVALID);
    }

    private Boolean asBoolean(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof String s && StringUtils.hasText(s)) {
            String trimmed = s.trim().toLowerCase();
            if ("true".equals(trimmed) || "false".equals(trimmed)) {
                return Boolean.parseBoolean(trimmed);
            }
        }
        throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_VALUE_INVALID);
    }

    private String[] asStringArray(Object raw) {
        if (raw instanceof String[] arr) {
            return filterBlank(arr);
        }
        if (raw instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                if (!(item instanceof String s) || !StringUtils.hasText(s)) {
                    throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_VALUE_INVALID);
                }
                result.add(s);
            }
            return result.toArray(new String[0]);
        }
        if (raw instanceof String s && StringUtils.hasText(s)) {
            return new String[]{s};
        }
        throw new BizException(ErrorCode.DEVICE_CUSTOM_FIELD_VALUE_INVALID);
    }

    private String[] filterBlank(String[] values) {
        if (values == null || values.length == 0) {
            return values;
        }
        List<String> result = new ArrayList<>();
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                result.add(v);
            }
        }
        return result.toArray(new String[0]);
    }
}
