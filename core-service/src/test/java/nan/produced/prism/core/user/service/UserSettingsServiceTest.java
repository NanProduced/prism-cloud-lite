package nan.produced.prism.core.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.user.domain.UserProfileEntity;
import nan.produced.prism.core.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class UserSettingsServiceTest {

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private UserSettingsService userSettingsService;

    private UserProfileEntity mockProfile;
    private ObjectMapper realObjectMapper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        realObjectMapper = new ObjectMapper();
        mockProfile = UserProfileEntity.builder()
                .id(UUID.randomUUID())
                .publicId("test-public-id")
                .configs(new HashMap<>())
                .build();
    }

    @Test
    @DisplayName("获取当前用户设置覆盖 - 成功")
    void testGetCurrentUserSettingsOverrides_Success() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("theme", "dark");
        Map<String, Object> ui = new HashMap<>();
        ui.put("language", "zh-CN");
        Map<String, Object> configs = new HashMap<>();
        configs.put("settings", settings);
        configs.put("ui", ui);
        mockProfile.setConfigs(configs);

        when(userProfileService.getOrCreateCurrentUserProfile()).thenReturn(mockProfile);

        var result = userSettingsService.getCurrentUserSettingsOverrides();

        assertThat(result).isNotNull();
        assertThat(result.settings()).containsEntry("theme", "dark");
        assertThat(result.ui()).containsEntry("language", "zh-CN");
    }

    @Test
    @DisplayName("获取当前用户设置覆盖 - 无配置")
    void testGetCurrentUserSettingsOverrides_NoConfigs() {
        mockProfile.setConfigs(null);

        when(userProfileService.getOrCreateCurrentUserProfile()).thenReturn(mockProfile);

        var result = userSettingsService.getCurrentUserSettingsOverrides();

        assertThat(result).isNotNull();
        assertThat(result.settings()).isEmpty();
        assertThat(result.ui()).isEmpty();
    }

    @Test
    @DisplayName("更新当前用户设置覆盖 - 成功")
    void testPatchCurrentUserSettingsOverrides_Success() throws Exception {
        Map<String, Object> initialSettings = new HashMap<>();
        initialSettings.put("theme", "light");
        Map<String, Object> configs = new HashMap<>();
        configs.put("settings", initialSettings);
        mockProfile.setConfigs(configs);

        when(userProfileService.getOrCreateCurrentUserProfile()).thenReturn(mockProfile);
        when(userProfileRepository.save(any(UserProfileEntity.class))).thenAnswer(invocation -> {
            UserProfileEntity savedProfile = invocation.getArgument(0);
            mockProfile.setConfigs(savedProfile.getConfigs());
            return savedProfile;
        });

        ObjectNode patch = realObjectMapper.createObjectNode();
        ObjectNode settingsPatch = realObjectMapper.createObjectNode();
        settingsPatch.put("theme", "dark");
        settingsPatch.put("fontSize", "medium");
        patch.set("settings", settingsPatch);

        var result = userSettingsService.patchCurrentUserSettingsOverrides(patch);

        assertThat(result).isNotNull();
        // 验证save方法被调用
        verify(userProfileRepository, times(1)).save(any(UserProfileEntity.class));
    }

    @Test
    @DisplayName("更新当前用户设置覆盖 - 空patch")
    void testPatchCurrentUserSettingsOverrides_EmptyPatch() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("theme", "light");
        Map<String, Object> configs = new HashMap<>();
        configs.put("settings", settings);
        mockProfile.setConfigs(configs);

        when(userProfileService.getOrCreateCurrentUserProfile()).thenReturn(mockProfile);

        var result = userSettingsService.patchCurrentUserSettingsOverrides(null);

        assertThat(result).isNotNull();
        assertThat(result.settings()).containsEntry("theme", "light");
    }

    @Test
    @DisplayName("更新当前用户设置覆盖 - 无效的section格式")
    void testPatchCurrentUserSettingsOverrides_InvalidSectionFormat() throws Exception {
        when(userProfileService.getOrCreateCurrentUserProfile()).thenReturn(mockProfile);

        ObjectNode patch = realObjectMapper.createObjectNode();
        patch.put("settings", "not an object"); // 无效的格式，应该是对象

        assertThatThrownBy(() -> userSettingsService.patchCurrentUserSettingsOverrides(patch))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("settings must be a JSON object or null");
    }

    @Test
    @DisplayName("更新当前用户设置覆盖 - 删除section")
    void testPatchCurrentUserSettingsOverrides_DeleteSection() throws Exception {
        Map<String, Object> settings = new HashMap<>();
        settings.put("theme", "light");
        Map<String, Object> configs = new HashMap<>();
        configs.put("settings", settings);
        mockProfile.setConfigs(configs);

        when(userProfileService.getOrCreateCurrentUserProfile()).thenReturn(mockProfile);
        when(userProfileRepository.save(any(UserProfileEntity.class))).thenReturn(mockProfile);

        ObjectNode patch = realObjectMapper.createObjectNode();
        patch.set("settings", null); // 删除settings section

        var result = userSettingsService.patchCurrentUserSettingsOverrides(patch);

        assertThat(result).isNotNull();
        assertThat(result.settings()).isEmpty();
    }
}
