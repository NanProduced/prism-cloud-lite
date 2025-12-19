package nan.produced.prism.core.user.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.util.JsonUtils;
import nan.produced.prism.core.user.domain.UserProfileEntity;
import nan.produced.prism.core.user.dto.UserSettingsOverridesView;
import nan.produced.prism.core.user.repository.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private static final String KEY_SETTINGS = "settings";
    private static final String KEY_UI = "ui";

    private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {};

    private final UserProfileService userProfileService;
    private final UserProfileRepository userProfileRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public UserSettingsOverridesView getCurrentUserSettingsOverrides() {
        UserProfileEntity profile = userProfileService.getOrCreateCurrentUserProfile();
        Map<String, Object> configs = profile.getConfigs();
        Map<String, Object> settings = extractMap(configs, KEY_SETTINGS);
        Map<String, Object> ui = extractMap(configs, KEY_UI);
        return new UserSettingsOverridesView(settings, ui);
    }

    @Transactional
    public UserSettingsOverridesView patchCurrentUserSettingsOverrides(ObjectNode patch) {
        if (patch == null || patch.isEmpty()) {
            return getCurrentUserSettingsOverrides();
        }

        UserProfileEntity profile = userProfileService.getOrCreateCurrentUserProfile();
        Map<String, Object> updatedConfigs = new HashMap<>(profile.getConfigs() == null ? Map.of() : profile.getConfigs());

        applyPatchSection(updatedConfigs, patch, KEY_SETTINGS);
        applyPatchSection(updatedConfigs, patch, KEY_UI);

        // 仅保存 overrides：两个 section 都为空时，直接保存空对象
        if (updatedConfigs.isEmpty()) {
            profile.setConfigs(new HashMap<>());
        }
        else {
            profile.setConfigs(updatedConfigs);
        }

        userProfileRepository.save(profile);
        return new UserSettingsOverridesView(
            extractMap(updatedConfigs, KEY_SETTINGS),
            extractMap(updatedConfigs, KEY_UI)
        );
    }

    private void applyPatchSection(Map<String, Object> configs, ObjectNode patch, String sectionKey) {
        if (!patch.has(sectionKey)) {
            return;
        }

        JsonNode node = patch.get(sectionKey);
        if (node == null || node.isNull()) {
            configs.remove(sectionKey);
            return;
        }
        if (!node.isObject()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, sectionKey + " must be a JSON object or null");
        }

        Map<String, Object> patchMap = objectMapper.convertValue(node, MAP_TYPE_REF);
        Map<String, Object> current = extractMap(configs, sectionKey);
        Map<String, Object> merged = JsonUtils.mergePatch(current, patchMap);
        if (merged.isEmpty()) {
            configs.remove(sectionKey);
        }
        else {
            configs.put(sectionKey, merged);
        }
    }

    private Map<String, Object> extractMap(Map<String, Object> root, String key) {
        if (root == null || root.isEmpty()) {
            return new HashMap<>();
        }
        Object value = root.get(key);
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> normalized = new HashMap<>();
            raw.forEach((k, v) -> {
                if (k != null) {
                    normalized.put(k.toString(), v);
                }
            });
            return normalized;
        }
        // 非对象结构，视为无效存储，避免影响前端渲染
        return new HashMap<>();
    }
}
