package nan.produced.prism.core.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Map;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.user.domain.UserProfileEntity;
import nan.produced.prism.core.user.dto.UserSettingsOverridesView;
import nan.produced.prism.core.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserSettingsService 单元测试")
class UserSettingsServiceTests {

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private UserSettingsService userSettingsService;

    private static final String KEY_SETTINGS = "settings";
    private static final String KEY_UI = "ui";

    private UserProfileEntity createTestProfile(Map<String, Object> configs) {
        return UserProfileEntity.builder()
                .id(java.util.UUID.randomUUID())
                .publicId("test_public_id")
                .email("test@example.com")
                .displayName("Test User")
                .subscriptionTier("FREE")
                .metadata(new HashMap<>())
                .configs(configs)
                .build();
    }

    @Nested
    @DisplayName("getCurrentUserSettingsOverrides 方法测试")
    class GetCurrentUserSettingsOverridesTests {

        @Test
        @DisplayName("当configs为null时，返回空的settings和ui")
        void whenConfigsIsNull_shouldReturnEmptyMaps() {
            UserProfileEntity profile = createTestProfile(null);
            when(userProfileService.getOrCreateCurrentUserProfile()).thenReturn(profile);

            UserSettingsOverridesView result = userSettingsService.getCurrentUserSettingsOverrides();

            assertThat(result).isNotNull();
            assertThat(result.settings()).isEmpty();
            assertThat(result.ui()).isEmpty();
        }

        @Test
        @DisplayName("当configs为空时，返回空的settings和ui")
        void whenConfigsIsEmpty_shouldReturnEmptyMaps() {
            UserProfileEntity profile = createTestProfile(new HashMap<>());
            when(userProfileService.getOrCreateCurrentUserProfile()).thenReturn(profile);

            UserSettingsOverridesView result = userSettingsService.getCurrentUserSettingsOverrides();

            assertThat(result).isNotNull();
            assertThat(result.settings()).isEmpty();
            assertThat(result.ui()).isEmpty();
        }

        @Test
        @DisplayName("当configs包含settings和ui时，正确提取并返回")
        void whenConfigsHasSettingsAndUi_shouldExtractCorrectly() {
            Map<String, Object> settings = new HashMap<>();
            settings.put("theme", "dark");
            settings.put("language", "zh-CN");

            Map<String, Object> ui = new HashMap<>();
            ui.put("sidebarCollapsed", true);
            ui.put("fontSize", 14);

            Map<String, Object> configs = new HashMap<>();
            configs.put(KEY_SETTINGS, settings);
            configs.put(KEY_UI, ui);

            UserProfileEntity profile = createTestProfile(configs);
            when(userProfileService.getOrCreateCurrentUserProfile()).thenReturn(profile);

            UserSettingsOverridesView result = userSettingsService.getCurrentUserSettingsOverrides();

            assertThat(result).isNotNull();
            assertThat(result.settings()).containsEntry("theme", "dark");
            assertThat(result.settings()).containsEntry("language", "zh-CN");
            assertThat(result.ui()).containsEntry("sidebarCollapsed", true);
            assertThat(result.ui()).containsEntry("fontSize", 14);
        }

        @Test
        @DisplayName("当settings不是Map类型时，返回空Map")
        void whenSettingsIsNotMap_shouldReturnEmptyMap() {
            Map<String, Object> configs = new HashMap<>();
            configs.put(KEY_SETTINGS, "not a map");

            UserProfileEntity profile = createTestProfile(configs);
            when(userProfileService.getOrCreateCurrentUserProfile()).thenReturn(profile);

            UserSettingsOverridesView result = userSettingsService.getCurrentUserSettingsOverrides();

            assertThat(result.settings()).isEmpty();
        }

        @Test
        @DisplayName("当ui不是Map类型时，返回空Map")
        void whenUiIsNotMap_shouldReturnEmptyMap() {
            Map<String, Object> configs = new HashMap<>();
            configs.put(KEY_UI, 123);

            UserProfileEntity profile = createTestProfile(configs);
            when(userProfileService.getOrCreateCurrentUserProfile()).thenReturn(profile);

            UserSettingsOverridesView result = userSettingsService.getCurrentUserSettingsOverrides();

            assertThat(result.ui()).isEmpty();
        }
    }

    @Nested
    @DisplayName("patchCurrentUserSettingsOverrides 方法测试")
    class PatchCurrentUserSettingsOverridesTests {

        @Test
        @DisplayName("当patch为null时，返回当前设置")
        void whenPatchIsNull_shouldReturnCurrentSettings() {
            Map<String, Object> settings = new HashMap<>();
            settings.put("theme", "dark");

            Map<String, Object> configs = new HashMap<>();
            configs.put(KEY_SETTINGS, settings);

            UserProfileEntity profile = createTestProfile(configs);
            when(userProfileService.getOrCreateCurrentUserProfile()).thenReturn(profile);

            UserSettingsOverridesView result = userSettingsService.patchCurrentUserSettingsOverrides(null);

            assertThat(result.settings()).containsEntry("theme", "dark");
            verify(userProfileRepository, never()).save(any(UserProfileEntity.class));
        }

        @Test
        @DisplayName("当patch为空时，返回当前设置")
        void whenPatchIsEmpty_shouldReturnCurrentSettings() {
            Map<String, Object> settings = new HashMap<>();
            settings.put("theme", "dark");

            Map<String, Object> configs = new HashMap<>();
            configs.put(KEY_SETTINGS, settings);

            UserProfileEntity profile = createTestProfile(configs);
            when(userProfileService.getOrCreateCurrentUserProfile()).thenReturn(profile);

            ObjectNode emptyPatch = objectMapper.createObjectNode();
            UserSettingsOverridesView result = userSettingsService.patchCurrentUserSettingsOverrides(emptyPatch);

            assertThat(result.settings()).containsEntry("theme", "dark");
            verify(userProfileRepository, never()).save(any(UserProfileEntity.class));
        }

        @Test
        @DisplayName("当patch包含settings时，正确合并并保存")
        void whenPatchHasSettings_shouldMergeAndSave() {
            Map<String, Object> existingSettings = new HashMap<>();
            existingSettings.put("theme", "dark");
            existingSettings.put("language", "zh-CN");

            Map<String, Object> configs = new HashMap<>();
            configs.put(KEY_SETTINGS, existingSettings);

            UserProfileEntity profile = createTestProfile(configs);
            when(userProfileService.getOrCreateCurrentUserProfile()).thenReturn(profile);
            when(userProfileRepository.save(any(UserProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ObjectNode patch = objectMapper.createObjectNode();
            ObjectNode settingsPatch = patch.putObject(KEY_SETTINGS);
            settingsPatch.put("theme", "light");
            settingsPatch.put("notifications", true);

            UserSettingsOverridesView result = userSettingsService.patchCurrentUserSettingsOverrides(patch);

            assertThat(result.settings()).containsEntry("theme", "light");
            assertThat(result.settings()).containsEntry("language", "zh-CN");
            assertThat(result.settings()).containsEntry("notifications", true);
            verify(userProfileRepository).save(profile);
        }

        @Test
        @DisplayName("当patch包含ui时，正确合并并保存")
        void whenPatchHasUi_shouldMergeAndSave() {
            Map<String, Object> existingUi = new HashMap<>();
            existingUi.put("sidebarCollapsed", false);
            existingUi.put("fontSize", 14);

            Map<String, Object> configs = new HashMap<>();
            configs.put(KEY_UI, existingUi);

            UserProfileEntity profile = createTestProfile(configs);
            when(userProfileService.getOrCreateCurrentUserProfile()).thenReturn(profile);
            when(userProfileRepository.save(any(UserProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ObjectNode patch = objectMapper.createObjectNode();
            ObjectNode uiPatch = patch.putObject(KEY_UI);
            uiPatch.put("sidebarCollapsed", true);
            uiPatch.put("density", "compact");

            UserSettingsOverridesView result = userSettingsService.patchCurrentUserSettingsOverrides(patch);

            assertThat(result.ui()).containsEntry("sidebarCollapsed", true);
            assertThat(result.ui()).containsEntry("fontSize", 14);
            assertThat(result.ui()).containsEntry("density", "compact");
            verify(userProfileRepository).save(profile);
        }

        @Test
        @DisplayName("当patch中settings值为null时，删除该key")
        void whenPatchSettingsValueIsNull_shouldRemoveKey() {
            Map<String, Object> existingSettings = new HashMap<>();
            existingSettings.put("theme", "dark");
            existingSettings.put("language", "zh-CN");

            Map<String, Object> configs = new HashMap<>();
            configs.put(KEY_SETTINGS, existingSettings);

            UserProfileEntity profile = createTestProfile(configs);
            when(userProfileService.getOrCreateCurrentUserProfile()).thenReturn(profile);
            when(userProfileRepository.save(any(UserProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ObjectNode patch = objectMapper.createObjectNode();
            ObjectNode settingsPatch = patch.putObject(KEY_SETTINGS);
            settingsPatch.putNull("theme");

            UserSettingsOverridesView result = userSettingsService.patchCurrentUserSettingsOverrides(patch);

            assertThat(result.settings()).doesNotContainKey("theme");
            assertThat(result.settings()).containsEntry("language", "zh-CN");
        }

        @Test
        @DisplayName("当patch中ui值为null时，删除该key")
        void whenPatchUiValueIsNull_shouldRemoveKey() {
            Map<String, Object> existingUi = new HashMap<>();
            existingUi.put("sidebarCollapsed", false);
            existingUi.put("fontSize", 14);

            Map<String, Object> configs = new HashMap<>();
            configs.put(KEY_UI, existingUi);

            UserProfileEntity profile = createTestProfile(configs);
            when(userProfileService.getOrCreateCurrentUserProfile()).thenReturn(profile);
            when(userProfileRepository.save(any(UserProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ObjectNode patch = objectMapper.createObjectNode();
            ObjectNode uiPatch = patch.putObject(KEY_UI);
            uiPatch.putNull("fontSize");

            UserSettingsOverridesView result = userSettingsService.patchCurrentUserSettingsOverrides(patch);

            assertThat(result.ui()).doesNotContainKey("fontSize");
            assertThat(result.ui()).containsEntry("sidebarCollapsed", false);
        }

        @Test
        @DisplayName("当patch中settings为null时，删除整个settings")
        void whenPatchSettingsIsNull_shouldRemoveEntireSettings() {
            Map<String, Object> existingSettings = new HashMap<>();
            existingSettings.put("theme", "dark");

            Map<String, Object> configs = new HashMap<>();
            configs.put(KEY_SETTINGS, existingSettings);
            configs.put(KEY_UI, new HashMap<>());

            UserProfileEntity profile = createTestProfile(configs);
            when(userProfileService.getOrCreateCurrentUserProfile()).thenReturn(profile);
            when(userProfileRepository.save(any(UserProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ObjectNode patch = objectMapper.createObjectNode();
            patch.putNull(KEY_SETTINGS);

            UserSettingsOverridesView result = userSettingsService.patchCurrentUserSettingsOverrides(patch);

            assertThat(result.settings()).isEmpty();
            assertThat(profile.getConfigs()).doesNotContainKey(KEY_SETTINGS);
        }

        @Test
        @DisplayName("当patch中ui为null时，删除整个ui")
        void whenPatchUiIsNull_shouldRemoveEntireUi() {
            Map<String, Object> existingUi = new HashMap<>();
            existingUi.put("sidebarCollapsed", false);

            Map<String, Object> configs = new HashMap<>();
            configs.put(KEY_SETTINGS, new HashMap<>());
            configs.put(KEY_UI, existingUi);

            UserProfileEntity profile = createTestProfile(configs);
            when(userProfileService.getOrCreateCurrentUserProfile()).thenReturn(profile);
            when(userProfileRepository.save(any(UserProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ObjectNode patch = objectMapper.createObjectNode();
            patch.putNull(KEY_UI);

            UserSettingsOverridesView result = userSettingsService.patchCurrentUserSettingsOverrides(patch);

            assertThat(result.ui()).isEmpty();
            assertThat(profile.getConfigs()).doesNotContainKey(KEY_UI);
        }

        @Test
        @DisplayName("当patch中settings不是Object类型时，抛出BizException")
        void whenPatchSettingsIsNotObject_shouldThrowBizException() {
            Map<String, Object> configs = new HashMap<>();
            configs.put(KEY_SETTINGS, new HashMap<>());

            UserProfileEntity profile = createTestProfile(configs);
            when(userProfileService.getOrCreateCurrentUserProfile()).thenReturn(profile);

            ObjectNode patch = objectMapper.createObjectNode();
            patch.put(KEY_SETTINGS, "not an object");

            assertThatThrownBy(() -> userSettingsService.patchCurrentUserSettingsOverrides(patch))
                    .isInstanceOf(BizException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.INVALID_REQUEST.getCode());
        }

        @Test
        @DisplayName("当patch中ui不是Object类型时，抛出BizException")
        void whenPatchUiIsNotObject_shouldThrowBizException() {
            Map<String, Object> configs = new HashMap<>();
            configs.put(KEY_UI, new HashMap<>());

            UserProfileEntity profile = createTestProfile(configs);
            when(userProfileService.getOrCreateCurrentUserProfile()).thenReturn(profile);

            ObjectNode patch = objectMapper.createObjectNode();
            patch.put(KEY_UI, 123);

            assertThatThrownBy(() -> userSettingsService.patchCurrentUserSettingsOverrides(patch))
                    .isInstanceOf(BizException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.INVALID_REQUEST.getCode());
        }

        @Test
        @DisplayName("当合并后settings为空时，从configs中删除")
        void whenSettingsEmptyAfterMerge_shouldRemoveFromConfigs() {
            Map<String, Object> existingSettings = new HashMap<>();
            existingSettings.put("theme", "dark");

            Map<String, Object> configs = new HashMap<>();
            configs.put(KEY_SETTINGS, existingSettings);

            UserProfileEntity profile = createTestProfile(configs);
            when(userProfileService.getOrCreateCurrentUserProfile()).thenReturn(profile);
            when(userProfileRepository.save(any(UserProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ObjectNode patch = objectMapper.createObjectNode();
            ObjectNode settingsPatch = patch.putObject(KEY_SETTINGS);
            settingsPatch.putNull("theme");

            UserSettingsOverridesView result = userSettingsService.patchCurrentUserSettingsOverrides(patch);

            assertThat(result.settings()).isEmpty();
            assertThat(profile.getConfigs()).doesNotContainKey(KEY_SETTINGS);
        }

        @Test
        @DisplayName("当合并后ui为空时，从configs中删除")
        void whenUiEmptyAfterMerge_shouldRemoveFromConfigs() {
            Map<String, Object> existingUi = new HashMap<>();
            existingUi.put("sidebarCollapsed", false);

            Map<String, Object> configs = new HashMap<>();
            configs.put(KEY_UI, existingUi);

            UserProfileEntity profile = createTestProfile(configs);
            when(userProfileService.getOrCreateCurrentUserProfile()).thenReturn(profile);
            when(userProfileRepository.save(any(UserProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ObjectNode patch = objectMapper.createObjectNode();
            ObjectNode uiPatch = patch.putObject(KEY_UI);
            uiPatch.putNull("sidebarCollapsed");

            UserSettingsOverridesView result = userSettingsService.patchCurrentUserSettingsOverrides(patch);

            assertThat(result.ui()).isEmpty();
            assertThat(profile.getConfigs()).doesNotContainKey(KEY_UI);
        }

        @Test
        @DisplayName("当configs为null时，正确处理patch")
        void whenConfigsIsNull_shouldHandlePatchCorrectly() {
            UserProfileEntity profile = createTestProfile(null);
            when(userProfileService.getOrCreateCurrentUserProfile()).thenReturn(profile);
            when(userProfileRepository.save(any(UserProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ObjectNode patch = objectMapper.createObjectNode();
            ObjectNode settingsPatch = patch.putObject(KEY_SETTINGS);
            settingsPatch.put("theme", "dark");

            UserSettingsOverridesView result = userSettingsService.patchCurrentUserSettingsOverrides(patch);

            assertThat(result.settings()).containsEntry("theme", "dark");
            assertThat(profile.getConfigs()).isNotNull();
            assertThat(profile.getConfigs()).containsKey(KEY_SETTINGS);
        }

        @Test
        @DisplayName("当configs为空时，正确处理patch")
        void whenConfigsIsEmpty_shouldHandlePatchCorrectly() {
            UserProfileEntity profile = createTestProfile(new HashMap<>());
            when(userProfileService.getOrCreateCurrentUserProfile()).thenReturn(profile);
            when(userProfileRepository.save(any(UserProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ObjectNode patch = objectMapper.createObjectNode();
            ObjectNode uiPatch = patch.putObject(KEY_UI);
            uiPatch.put("sidebarCollapsed", true);

            UserSettingsOverridesView result = userSettingsService.patchCurrentUserSettingsOverrides(patch);

            assertThat(result.ui()).containsEntry("sidebarCollapsed", true);
            assertThat(profile.getConfigs()).containsKey(KEY_UI);
        }

        @Test
        @DisplayName("当settings和ui都为空时，保存空的configs")
        void whenBothSettingsAndUiEmpty_shouldSaveEmptyConfigs() {
            Map<String, Object> existingSettings = new HashMap<>();
            existingSettings.put("theme", "dark");

            Map<String, Object> existingUi = new HashMap<>();
            existingUi.put("sidebarCollapsed", false);

            Map<String, Object> configs = new HashMap<>();
            configs.put(KEY_SETTINGS, existingSettings);
            configs.put(KEY_UI, existingUi);

            UserProfileEntity profile = createTestProfile(configs);
            when(userProfileService.getOrCreateCurrentUserProfile()).thenReturn(profile);
            when(userProfileRepository.save(any(UserProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ObjectNode patch = objectMapper.createObjectNode();
            ObjectNode settingsPatch = patch.putObject(KEY_SETTINGS);
            settingsPatch.putNull("theme");
            ObjectNode uiPatch = patch.putObject(KEY_UI);
            uiPatch.putNull("sidebarCollapsed");

            UserSettingsOverridesView result = userSettingsService.patchCurrentUserSettingsOverrides(patch);

            assertThat(result.settings()).isEmpty();
            assertThat(result.ui()).isEmpty();
            assertThat(profile.getConfigs()).isEmpty();
        }
    }
}
