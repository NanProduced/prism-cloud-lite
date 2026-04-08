package nan.produced.prism.core.user.service;

import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.core.security.api.CloudAuthContext;
import nan.produced.prism.core.security.api.CloudAuthUser;
import nan.produced.prism.core.system.api.SubscriptionQuota;
import nan.produced.prism.core.system.api.SubscriptionQuotaFacade;
import nan.produced.prism.core.user.api.StorageFileType;
import nan.produced.prism.core.user.api.StorageSourceType;
import nan.produced.prism.core.user.domain.UserQuotaUsageEntity;
import nan.produced.prism.core.user.domain.storage.UserStorageUsageEntity;
import nan.produced.prism.core.user.repository.UserQuotaUsageRepository;
import nan.produced.prism.core.user.repository.UserStorageUsageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserStorageUsageService 单元测试")
class UserStorageUsageServiceTests {

    @Mock
    private UserStorageUsageRepository userStorageUsageRepository;

    @Mock
    private UserQuotaUsageRepository userQuotaUsageRepository;

    @Mock
    private SubscriptionQuotaFacade subscriptionQuotaFacade;

    @Mock
    private UserQuotaSignalPublisher userQuotaSignalPublisher;

    @InjectMocks
    private UserStorageUsageService userStorageUsageService;

    private MockedStatic<CloudAuthContext> cloudAuthContextMock;

    private static final UUID TEST_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String TEST_TIER_FREE = "FREE";
    private static final String TEST_TIER_PRO = "PRO";
    private static final StorageSourceType TEST_SOURCE_TYPE = StorageSourceType.MEDIA_LIBRARY;
    private static final StorageFileType TEST_FILE_TYPE = StorageFileType.IMAGE;

    @BeforeEach
    void setUp() {
        cloudAuthContextMock = mockStatic(CloudAuthContext.class);
    }

    @AfterEach
    void tearDown() {
        cloudAuthContextMock.close();
    }

    private void mockCurrentUser(String tier) {
        CloudAuthUser authUser = new CloudAuthUser("test_public_id", TEST_USER_ID.toString(), null, tier);
        when(CloudAuthContext.getCurrentUser()).thenReturn(authUser);
        when(CloudAuthContext.hasAuthenticatedUser()).thenReturn(true);
    }

    private void mockNoAuthenticatedUser() {
        when(CloudAuthContext.hasAuthenticatedUser()).thenReturn(false);
    }

    private UserQuotaUsageEntity createTestQuotaUsage(long storageBytes) {
        return UserQuotaUsageEntity.builder()
                .id(UUID.randomUUID())
                .userId(TEST_USER_ID)
                .deviceCount(0)
                .customColumnCount(0)
                .storageTotalBytes(storageBytes)
                .version(0)
                .build();
    }

    private SubscriptionQuota createTestQuota(Long storageLimit) {
        return new SubscriptionQuota(10, storageLimit, 100, 10, 5);
    }

    @Nested
    @DisplayName("incrementUsage 方法测试")
    class IncrementUsageTests {

        @Test
        @DisplayName("当userId为null时，不执行任何操作")
        void whenUserIdIsNull_shouldDoNothing() {
            userStorageUsageService.incrementUsage(null, TEST_SOURCE_TYPE, TEST_FILE_TYPE, 1, 1000L);

            verify(userStorageUsageRepository, never()).incrementUsage(any(), any(), any(), anyInt(), anyLong());
            verify(userStorageUsageRepository, never()).save(any(UserStorageUsageEntity.class));
            verify(userQuotaUsageRepository, never()).incrementStorageTotalBytes(any(), anyLong());
        }

        @Test
        @DisplayName("当sourceType为null时，不执行任何操作")
        void whenSourceTypeIsNull_shouldDoNothing() {
            userStorageUsageService.incrementUsage(TEST_USER_ID, null, TEST_FILE_TYPE, 1, 1000L);

            verify(userStorageUsageRepository, never()).incrementUsage(any(), any(), any(), anyInt(), anyLong());
            verify(userStorageUsageRepository, never()).save(any(UserStorageUsageEntity.class));
            verify(userQuotaUsageRepository, never()).incrementStorageTotalBytes(any(), anyLong());
        }

        @Test
        @DisplayName("当fileType为null时，不执行任何操作")
        void whenFileTypeIsNull_shouldDoNothing() {
            userStorageUsageService.incrementUsage(TEST_USER_ID, TEST_SOURCE_TYPE, null, 1, 1000L);

            verify(userStorageUsageRepository, never()).incrementUsage(any(), any(), any(), anyInt(), anyLong());
            verify(userStorageUsageRepository, never()).save(any(UserStorageUsageEntity.class));
            verify(userQuotaUsageRepository, never()).incrementStorageTotalBytes(any(), anyLong());
        }

        @Test
        @DisplayName("当fileCount和bytes都为0时，不执行任何操作")
        void whenBothFileCountAndBytesAreZero_shouldDoNothing() {
            userStorageUsageService.incrementUsage(TEST_USER_ID, TEST_SOURCE_TYPE, TEST_FILE_TYPE, 0, 0L);

            verify(userStorageUsageRepository, never()).incrementUsage(any(), any(), any(), anyInt(), anyLong());
            verify(userStorageUsageRepository, never()).save(any(UserStorageUsageEntity.class));
            verify(userQuotaUsageRepository, never()).incrementStorageTotalBytes(any(), anyLong());
        }

        @Test
        @DisplayName("当fileCount为负数时，应视为0处理")
        void whenFileCountIsNegative_shouldTreatAsZero() {
            UserQuotaUsageEntity quotaUsage = createTestQuotaUsage(5000L);

            when(userStorageUsageRepository.incrementUsage(
                    eq(TEST_USER_ID), eq(TEST_SOURCE_TYPE), eq(TEST_FILE_TYPE), eq(0), eq(1000L))).thenReturn(1);
            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(quotaUsage));
            mockNoAuthenticatedUser();

            userStorageUsageService.incrementUsage(TEST_USER_ID, TEST_SOURCE_TYPE, TEST_FILE_TYPE, -5, 1000L);

            verify(userStorageUsageRepository).incrementUsage(TEST_USER_ID, TEST_SOURCE_TYPE, TEST_FILE_TYPE, 0, 1000L);
            verify(userQuotaUsageRepository).incrementStorageTotalBytes(TEST_USER_ID, 1000L);
        }

        @Test
        @DisplayName("当bytes为负数时，应视为0处理")
        void whenBytesIsNegative_shouldTreatAsZero() {
            when(userStorageUsageRepository.incrementUsage(
                    eq(TEST_USER_ID), eq(TEST_SOURCE_TYPE), eq(TEST_FILE_TYPE), eq(2), eq(0L))).thenReturn(1);

            userStorageUsageService.incrementUsage(TEST_USER_ID, TEST_SOURCE_TYPE, TEST_FILE_TYPE, 2, -1000L);

            verify(userStorageUsageRepository).incrementUsage(TEST_USER_ID, TEST_SOURCE_TYPE, TEST_FILE_TYPE, 2, 0L);
            verify(userQuotaUsageRepository, never()).incrementStorageTotalBytes(any(), anyLong());
            verify(userQuotaSignalPublisher, never()).publishQuotaUpdated(any(), any(), any(), any(), anyLong(), any());
        }

        @Test
        @DisplayName("当存储记录已存在时，直接更新使用量")
        void whenStorageRecordExists_shouldUpdateDirectly() {
            UserQuotaUsageEntity quotaUsage = createTestQuotaUsage(5000L);
            SubscriptionQuota quota = createTestQuota(1000000000L);

            when(userStorageUsageRepository.incrementUsage(
                    eq(TEST_USER_ID), eq(TEST_SOURCE_TYPE), eq(TEST_FILE_TYPE), eq(2), eq(1000L))).thenReturn(1);
            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(quotaUsage));
            mockCurrentUser(TEST_TIER_FREE);
            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);

            userStorageUsageService.incrementUsage(TEST_USER_ID, TEST_SOURCE_TYPE, TEST_FILE_TYPE, 2, 1000L);

            verify(userStorageUsageRepository, never()).save(any(UserStorageUsageEntity.class));
            verify(userQuotaUsageRepository).incrementStorageTotalBytes(TEST_USER_ID, 1000L);
            verify(userQuotaSignalPublisher).publishQuotaUpdated(eq(TEST_USER_ID), eq(TEST_TIER_FREE),
                    eq(UserQuotaSignalPublisher.RESOURCE_STORAGE_BYTES), eq("bytes"), eq(5000L), eq(1000000000L));
        }

        @Test
        @DisplayName("当存储记录不存在时，创建新记录")
        void whenStorageRecordNotExists_shouldCreateNewRecord() {
            UserQuotaUsageEntity quotaUsage = createTestQuotaUsage(5000L);
            SubscriptionQuota quota = createTestQuota(1000000000L);

            when(userStorageUsageRepository.incrementUsage(
                    eq(TEST_USER_ID), eq(TEST_SOURCE_TYPE), eq(TEST_FILE_TYPE), eq(2), eq(1000L))).thenReturn(0);
            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(quotaUsage));
            mockCurrentUser(TEST_TIER_FREE);
            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);

            userStorageUsageService.incrementUsage(TEST_USER_ID, TEST_SOURCE_TYPE, TEST_FILE_TYPE, 2, 1000L);

            verify(userStorageUsageRepository).save(any(UserStorageUsageEntity.class));
            verify(userQuotaUsageRepository).incrementStorageTotalBytes(TEST_USER_ID, 1000L);
        }

        @Test
        @DisplayName("当bytes为0时，不更新配额使用记录")
        void whenBytesIsZero_shouldNotUpdateQuotaUsage() {
            when(userStorageUsageRepository.incrementUsage(
                    eq(TEST_USER_ID), eq(TEST_SOURCE_TYPE), eq(TEST_FILE_TYPE), eq(2), eq(0L))).thenReturn(1);

            userStorageUsageService.incrementUsage(TEST_USER_ID, TEST_SOURCE_TYPE, TEST_FILE_TYPE, 2, 0L);

            verify(userQuotaUsageRepository, never()).incrementStorageTotalBytes(any(), anyLong());
            verify(userQuotaSignalPublisher, never()).publishQuotaUpdated(any(), any(), any(), any(), anyLong(), any());
        }

        @Test
        @DisplayName("当没有认证用户时，发布事件时tier为null")
        void whenNoAuthenticatedUser_shouldPublishWithNullTier() {
            UserQuotaUsageEntity quotaUsage = createTestQuotaUsage(5000L);

            when(userStorageUsageRepository.incrementUsage(
                    eq(TEST_USER_ID), eq(TEST_SOURCE_TYPE), eq(TEST_FILE_TYPE), eq(2), eq(1000L))).thenReturn(1);
            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(quotaUsage));
            mockNoAuthenticatedUser();

            userStorageUsageService.incrementUsage(TEST_USER_ID, TEST_SOURCE_TYPE, TEST_FILE_TYPE, 2, 1000L);

            verify(userQuotaSignalPublisher).publishQuotaUpdated(eq(TEST_USER_ID), eq(null),
                    eq(UserQuotaSignalPublisher.RESOURCE_STORAGE_BYTES), eq("bytes"), eq(5000L), eq(null));
        }

        @Test
        @DisplayName("当配额使用记录不存在时，不发布更新事件")
        void whenQuotaUsageNotExists_shouldNotPublishUpdate() {
            when(userStorageUsageRepository.incrementUsage(
                    eq(TEST_USER_ID), eq(TEST_SOURCE_TYPE), eq(TEST_FILE_TYPE), eq(2), eq(1000L))).thenReturn(1);
            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());

            userStorageUsageService.incrementUsage(TEST_USER_ID, TEST_SOURCE_TYPE, TEST_FILE_TYPE, 2, 1000L);

            verify(userQuotaSignalPublisher, never()).publishQuotaUpdated(any(), any(), any(), any(), anyLong(), any());
        }

        @Test
        @DisplayName("当使用不同的sourceType和fileType时，应正确传递参数")
        void whenUsingDifferentSourceAndFileType_shouldPassCorrectParams() {
            UserQuotaUsageEntity quotaUsage = createTestQuotaUsage(5000L);

            when(userStorageUsageRepository.incrementUsage(
                    eq(TEST_USER_ID),
                    eq(StorageSourceType.EXPORT),
                    eq(StorageFileType.DOCUMENT),
                    eq(1),
                    eq(5000L))).thenReturn(1);
            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(quotaUsage));
            mockNoAuthenticatedUser();

            userStorageUsageService.incrementUsage(TEST_USER_ID, StorageSourceType.EXPORT, StorageFileType.DOCUMENT, 1, 5000L);

            verify(userStorageUsageRepository).incrementUsage(
                    TEST_USER_ID,
                    StorageSourceType.EXPORT,
                    StorageFileType.DOCUMENT,
                    1,
                    5000L);
        }
    }

    @Nested
    @DisplayName("decrementUsage 方法测试")
    class DecrementUsageTests {

        @Test
        @DisplayName("当userId为null时，不执行任何操作")
        void whenUserIdIsNull_shouldDoNothing() {
            userStorageUsageService.decrementUsage(null, TEST_SOURCE_TYPE, TEST_FILE_TYPE, 1, 1000L);

            verify(userStorageUsageRepository, never()).decrementUsage(any(), any(), any(), anyInt(), anyLong());
            verify(userQuotaUsageRepository, never()).incrementStorageTotalBytes(any(), anyLong());
        }

        @Test
        @DisplayName("当sourceType为null时，不执行任何操作")
        void whenSourceTypeIsNull_shouldDoNothing() {
            userStorageUsageService.decrementUsage(TEST_USER_ID, null, TEST_FILE_TYPE, 1, 1000L);

            verify(userStorageUsageRepository, never()).decrementUsage(any(), any(), any(), anyInt(), anyLong());
            verify(userQuotaUsageRepository, never()).incrementStorageTotalBytes(any(), anyLong());
        }

        @Test
        @DisplayName("当fileType为null时，不执行任何操作")
        void whenFileTypeIsNull_shouldDoNothing() {
            userStorageUsageService.decrementUsage(TEST_USER_ID, TEST_SOURCE_TYPE, null, 1, 1000L);

            verify(userStorageUsageRepository, never()).decrementUsage(any(), any(), any(), anyInt(), anyLong());
            verify(userQuotaUsageRepository, never()).incrementStorageTotalBytes(any(), anyLong());
        }

        @Test
        @DisplayName("当fileCount和bytes都为0时，不执行任何操作")
        void whenBothFileCountAndBytesAreZero_shouldDoNothing() {
            userStorageUsageService.decrementUsage(TEST_USER_ID, TEST_SOURCE_TYPE, TEST_FILE_TYPE, 0, 0L);

            verify(userStorageUsageRepository, never()).decrementUsage(any(), any(), any(), anyInt(), anyLong());
            verify(userQuotaUsageRepository, never()).incrementStorageTotalBytes(any(), anyLong());
        }

        @Test
        @DisplayName("当fileCount为负数时，应视为0处理")
        void whenFileCountIsNegative_shouldTreatAsZero() {
            UserQuotaUsageEntity quotaUsage = createTestQuotaUsage(5000L);

            when(userStorageUsageRepository.decrementUsage(
                    eq(TEST_USER_ID), eq(TEST_SOURCE_TYPE), eq(TEST_FILE_TYPE), eq(0), eq(1000L))).thenReturn(1);
            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(quotaUsage));
            mockNoAuthenticatedUser();

            userStorageUsageService.decrementUsage(TEST_USER_ID, TEST_SOURCE_TYPE, TEST_FILE_TYPE, -5, 1000L);

            verify(userStorageUsageRepository).decrementUsage(TEST_USER_ID, TEST_SOURCE_TYPE, TEST_FILE_TYPE, 0, 1000L);
            verify(userQuotaUsageRepository).incrementStorageTotalBytes(TEST_USER_ID, -1000L);
        }

        @Test
        @DisplayName("当bytes为负数时，应视为0处理")
        void whenBytesIsNegative_shouldTreatAsZero() {
            when(userStorageUsageRepository.decrementUsage(
                    eq(TEST_USER_ID), eq(TEST_SOURCE_TYPE), eq(TEST_FILE_TYPE), eq(2), eq(0L))).thenReturn(1);

            userStorageUsageService.decrementUsage(TEST_USER_ID, TEST_SOURCE_TYPE, TEST_FILE_TYPE, 2, -1000L);

            verify(userStorageUsageRepository).decrementUsage(TEST_USER_ID, TEST_SOURCE_TYPE, TEST_FILE_TYPE, 2, 0L);
            verify(userQuotaUsageRepository, never()).incrementStorageTotalBytes(any(), anyLong());
            verify(userQuotaSignalPublisher, never()).publishQuotaUpdated(any(), any(), any(), any(), anyLong(), any());
        }

        @Test
        @DisplayName("当存储记录已存在时，减少使用量并更新配额")
        void whenStorageRecordExists_shouldDecrementAndUpdateQuota() {
            UserQuotaUsageEntity quotaUsage = createTestQuotaUsage(5000L);
            SubscriptionQuota quota = createTestQuota(10000000000L);

            when(userStorageUsageRepository.decrementUsage(
                    eq(TEST_USER_ID), eq(TEST_SOURCE_TYPE), eq(TEST_FILE_TYPE), eq(2), eq(1000L))).thenReturn(1);
            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(quotaUsage));
            mockCurrentUser(TEST_TIER_PRO);
            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_PRO))).thenReturn(quota);

            userStorageUsageService.decrementUsage(TEST_USER_ID, TEST_SOURCE_TYPE, TEST_FILE_TYPE, 2, 1000L);

            verify(userQuotaUsageRepository).incrementStorageTotalBytes(TEST_USER_ID, -1000L);
            verify(userQuotaSignalPublisher).publishQuotaUpdated(eq(TEST_USER_ID), eq(TEST_TIER_PRO),
                    eq(UserQuotaSignalPublisher.RESOURCE_STORAGE_BYTES), eq("bytes"), eq(5000L), eq(10000000000L));
        }

        @Test
        @DisplayName("当存储记录不存在时，不执行任何操作")
        void whenStorageRecordNotExists_shouldDoNothing() {
            when(userStorageUsageRepository.decrementUsage(
                    eq(TEST_USER_ID), eq(TEST_SOURCE_TYPE), eq(TEST_FILE_TYPE), eq(2), eq(1000L))).thenReturn(0);

            userStorageUsageService.decrementUsage(TEST_USER_ID, TEST_SOURCE_TYPE, TEST_FILE_TYPE, 2, 1000L);

            verify(userQuotaUsageRepository, never()).incrementStorageTotalBytes(any(), anyLong());
            verify(userQuotaSignalPublisher, never()).publishQuotaUpdated(any(), any(), any(), any(), anyLong(), any());
        }

        @Test
        @DisplayName("当bytes为0时，不更新配额使用记录")
        void whenBytesIsZero_shouldNotUpdateQuotaUsage() {
            when(userStorageUsageRepository.decrementUsage(
                    eq(TEST_USER_ID), eq(TEST_SOURCE_TYPE), eq(TEST_FILE_TYPE), eq(2), eq(0L))).thenReturn(1);

            userStorageUsageService.decrementUsage(TEST_USER_ID, TEST_SOURCE_TYPE, TEST_FILE_TYPE, 2, 0L);

            verify(userQuotaUsageRepository, never()).incrementStorageTotalBytes(any(), anyLong());
            verify(userQuotaSignalPublisher, never()).publishQuotaUpdated(any(), any(), any(), any(), anyLong(), any());
        }

        @Test
        @DisplayName("当配额使用记录不存在时，不发布更新事件")
        void whenQuotaUsageNotExists_shouldNotPublishUpdate() {
            when(userStorageUsageRepository.decrementUsage(
                    eq(TEST_USER_ID), eq(TEST_SOURCE_TYPE), eq(TEST_FILE_TYPE), eq(2), eq(1000L))).thenReturn(1);
            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());

            userStorageUsageService.decrementUsage(TEST_USER_ID, TEST_SOURCE_TYPE, TEST_FILE_TYPE, 2, 1000L);

            verify(userQuotaSignalPublisher, never()).publishQuotaUpdated(any(), any(), any(), any(), anyLong(), any());
        }

        @Test
        @DisplayName("当使用不同的sourceType和fileType时，应正确传递参数")
        void whenUsingDifferentSourceAndFileType_shouldPassCorrectParams() {
            UserQuotaUsageEntity quotaUsage = createTestQuotaUsage(5000L);

            when(userStorageUsageRepository.decrementUsage(
                    eq(TEST_USER_ID),
                    eq(StorageSourceType.SCREENSHOT),
                    eq(StorageFileType.VIDEO),
                    eq(1),
                    eq(5000L))).thenReturn(1);
            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(quotaUsage));
            mockNoAuthenticatedUser();

            userStorageUsageService.decrementUsage(TEST_USER_ID, StorageSourceType.SCREENSHOT, StorageFileType.VIDEO, 1, 5000L);

            verify(userStorageUsageRepository).decrementUsage(
                    TEST_USER_ID,
                    StorageSourceType.SCREENSHOT,
                    StorageFileType.VIDEO,
                    1,
                    5000L);
        }
    }
}
