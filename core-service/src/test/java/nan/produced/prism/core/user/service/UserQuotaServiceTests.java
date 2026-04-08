package nan.produced.prism.core.user.service;

import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.security.api.CloudAuthContext;
import nan.produced.prism.core.security.api.CloudAuthUser;
import nan.produced.prism.core.system.api.SubscriptionQuota;
import nan.produced.prism.core.system.api.SubscriptionQuotaFacade;
import nan.produced.prism.core.user.domain.UserQuotaUsageEntity;
import nan.produced.prism.core.user.repository.UserQuotaUsageRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserQuotaService 单元测试")
class UserQuotaServiceTests {

    @Mock
    private UserQuotaUsageRepository userQuotaUsageRepository;

    @Mock
    private SubscriptionQuotaFacade subscriptionQuotaFacade;

    @Mock
    private UserQuotaSignalPublisher userQuotaSignalPublisher;

    @InjectMocks
    private UserQuotaService userQuotaService;

    private MockedStatic<CloudAuthContext> cloudAuthContextMock;

    private static final UUID TEST_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String TEST_TIER_FREE = "FREE";
    private static final String TEST_TIER_PRO = "PRO";

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

    private UserQuotaUsageEntity createTestQuotaUsage(int deviceCount, int customColumnCount, long storageBytes) {
        return UserQuotaUsageEntity.builder()
                .id(UUID.randomUUID())
                .userId(TEST_USER_ID)
                .deviceCount(deviceCount)
                .customColumnCount(customColumnCount)
                .storageTotalBytes(storageBytes)
                .version(0)
                .build();
    }

    private SubscriptionQuota createTestQuota(Integer deviceLimit, Integer customColumnLimit, Long storageLimit) {
        return new SubscriptionQuota(deviceLimit, storageLimit, 100, 10, customColumnLimit);
    }

    @Nested
    @DisplayName("consumeDevices 方法测试")
    class ConsumeDevicesTests {

        @Test
        @DisplayName("当userId为null时，不执行任何操作")
        void whenUserIdIsNull_shouldDoNothing() {
            userQuotaService.consumeDevices(null, TEST_TIER_FREE, 1);

            verify(userQuotaUsageRepository, never()).findByUserId(any(UUID.class));
            verify(userQuotaUsageRepository, never()).incrementDeviceCountIfWithinLimit(any(UUID.class), anyInt(), anyInt());
            verify(subscriptionQuotaFacade, never()).getQuota(anyString());
        }

        @Test
        @DisplayName("当count小于等于0时，不执行任何操作")
        void whenCountIsZeroOrNegative_shouldDoNothing() {
            userQuotaService.consumeDevices(TEST_USER_ID, TEST_TIER_FREE, 0);
            userQuotaService.consumeDevices(TEST_USER_ID, TEST_TIER_FREE, -1);

            verify(userQuotaUsageRepository, never()).findByUserId(any(UUID.class));
            verify(userQuotaUsageRepository, never()).incrementDeviceCountIfWithinLimit(any(UUID.class), anyInt(), anyInt());
        }

        @Test
        @DisplayName("当配额使用记录不存在时，先创建记录再扣减额度")
        void whenQuotaUsageNotExists_shouldCreateFirstThenConsume() {
            SubscriptionQuota quota = createTestQuota(10, 5, 1000000000L);
            UserQuotaUsageEntity existingUsage = createTestQuotaUsage(2, 0, 0L);

            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(existingUsage));
            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);
            when(userQuotaUsageRepository.incrementDeviceCountIfWithinLimit(eq(TEST_USER_ID), eq(2), eq(10))).thenReturn(1);

            userQuotaService.consumeDevices(TEST_USER_ID, TEST_TIER_FREE, 2);

            verify(userQuotaUsageRepository).save(any(UserQuotaUsageEntity.class));
            verify(userQuotaUsageRepository).incrementDeviceCountIfWithinLimit(TEST_USER_ID, 2, 10);
            verify(userQuotaSignalPublisher).publishQuotaUpdated(eq(TEST_USER_ID), eq(TEST_TIER_FREE),
                    eq(UserQuotaSignalPublisher.RESOURCE_DEVICES), eq("count"), eq(2L), eq(10L));
        }

        @Test
        @DisplayName("当配额使用记录已存在时，直接扣减额度")
        void whenQuotaUsageExists_shouldConsumeDirectly() {
            SubscriptionQuota quota = createTestQuota(10, 5, 1000000000L);
            UserQuotaUsageEntity existingUsage = createTestQuotaUsage(3, 2, 500000000L);
            UserQuotaUsageEntity updatedUsage = createTestQuotaUsage(5, 2, 500000000L);

            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.of(existingUsage))
                    .thenReturn(Optional.of(updatedUsage));
            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);
            when(userQuotaUsageRepository.incrementDeviceCountIfWithinLimit(eq(TEST_USER_ID), eq(2), eq(10))).thenReturn(1);

            userQuotaService.consumeDevices(TEST_USER_ID, TEST_TIER_FREE, 2);

            verify(userQuotaUsageRepository, never()).save(any(UserQuotaUsageEntity.class));
            verify(userQuotaUsageRepository).incrementDeviceCountIfWithinLimit(TEST_USER_ID, 2, 10);
        }

        @Test
        @DisplayName("当扣减后超出配额时，抛出BizException并发布超限事件")
        void whenExceedsQuota_shouldThrowBizExceptionAndPublishExceeded() {
            SubscriptionQuota quota = createTestQuota(5, 5, 1000000000L);
            UserQuotaUsageEntity existingUsage = createTestQuotaUsage(5, 2, 500000000L);

            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(existingUsage));
            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);
            when(userQuotaUsageRepository.incrementDeviceCountIfWithinLimit(eq(TEST_USER_ID), eq(1), eq(5))).thenReturn(0);

            assertThatThrownBy(() -> userQuotaService.consumeDevices(TEST_USER_ID, TEST_TIER_FREE, 1))
                    .isInstanceOf(BizException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.DEVICE_QUOTA_EXCEEDED.getCode());

            verify(userQuotaSignalPublisher).publishQuotaExceeded(eq(TEST_USER_ID), eq(TEST_TIER_FREE),
                    eq(UserQuotaSignalPublisher.RESOURCE_DEVICES), eq("count"), eq(6L), eq(5L));
            verify(userQuotaSignalPublisher, never()).publishQuotaUpdated(any(), any(), any(), any(), anyLong(), any());
        }

        @Test
        @DisplayName("当配额限制为null时，不检查限制直接扣减")
        void whenLimitIsNull_shouldConsumeWithoutCheck() {
            SubscriptionQuota quota = createTestQuota(null, 5, 1000000000L);
            UserQuotaUsageEntity existingUsage = createTestQuotaUsage(100, 2, 500000000L);

            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(existingUsage));
            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);

            userQuotaService.consumeDevices(TEST_USER_ID, TEST_TIER_FREE, 10);

            verify(userQuotaUsageRepository).incrementDeviceCount(TEST_USER_ID, 10);
            verify(userQuotaUsageRepository, never()).incrementDeviceCountIfWithinLimit(any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("当配额限制为-1时，不检查限制直接扣减")
        void whenLimitIsNegativeOne_shouldConsumeWithoutCheck() {
            SubscriptionQuota quota = createTestQuota(-1, 5, 1000000000L);
            UserQuotaUsageEntity existingUsage = createTestQuotaUsage(100, 2, 500000000L);

            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(existingUsage));
            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);

            userQuotaService.consumeDevices(TEST_USER_ID, TEST_TIER_FREE, 10);

            verify(userQuotaUsageRepository).incrementDeviceCount(TEST_USER_ID, 10);
        }

        @Test
        @DisplayName("当tier为null时，使用FREE作为默认值")
        void whenTierIsNull_shouldUseFreeAsDefault() {
            SubscriptionQuota quota = createTestQuota(10, 5, 1000000000L);
            UserQuotaUsageEntity existingUsage = createTestQuotaUsage(3, 2, 500000000L);

            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(existingUsage));
            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);
            when(userQuotaUsageRepository.incrementDeviceCountIfWithinLimit(eq(TEST_USER_ID), eq(2), eq(10))).thenReturn(1);

            userQuotaService.consumeDevices(TEST_USER_ID, null, 2);

            verify(subscriptionQuotaFacade, org.mockito.Mockito.atLeast(1)).getQuota(TEST_TIER_FREE);
            verify(subscriptionQuotaFacade, never()).getQuota(TEST_TIER_PRO);
        }

        @Test
        @DisplayName("当tier为PRO时，使用PRO配额")
        void whenTierIsPro_shouldUseProQuota() {
            SubscriptionQuota quota = createTestQuota(100, 50, 10000000000L);
            UserQuotaUsageEntity existingUsage = createTestQuotaUsage(50, 10, 5000000000L);

            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(existingUsage));
            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_PRO))).thenReturn(quota);
            when(userQuotaUsageRepository.incrementDeviceCountIfWithinLimit(eq(TEST_USER_ID), eq(10), eq(100))).thenReturn(1);

            userQuotaService.consumeDevices(TEST_USER_ID, TEST_TIER_PRO, 10);

            verify(subscriptionQuotaFacade, org.mockito.Mockito.atLeast(1)).getQuota(TEST_TIER_PRO);
            verify(subscriptionQuotaFacade, never()).getQuota(TEST_TIER_FREE);
        }
    }

    @Nested
    @DisplayName("releaseDevices 方法测试")
    class ReleaseDevicesTests {

        @Test
        @DisplayName("当userId为null时，不执行任何操作")
        void whenUserIdIsNull_shouldDoNothing() {
            userQuotaService.releaseDevices(null, 1);

            verify(userQuotaUsageRepository, never()).findByUserId(any(UUID.class));
            verify(userQuotaUsageRepository, never()).save(any(UserQuotaUsageEntity.class));
        }

        @Test
        @DisplayName("当count小于等于0时，不执行任何操作")
        void whenCountIsZeroOrNegative_shouldDoNothing() {
            userQuotaService.releaseDevices(TEST_USER_ID, 0);
            userQuotaService.releaseDevices(TEST_USER_ID, -1);

            verify(userQuotaUsageRepository, never()).findByUserId(any(UUID.class));
            verify(userQuotaUsageRepository, never()).save(any(UserQuotaUsageEntity.class));
        }

        @Test
        @DisplayName("当配额使用记录不存在时，不执行任何操作")
        void whenQuotaUsageNotExists_shouldDoNothing() {
            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());

            userQuotaService.releaseDevices(TEST_USER_ID, 1);

            verify(userQuotaUsageRepository, never()).save(any(UserQuotaUsageEntity.class));
            verify(userQuotaSignalPublisher, never()).publishQuotaUpdated(any(), any(), any(), any(), anyLong(), any());
        }

        @Test
        @DisplayName("当配额使用记录存在时，释放设备额度并发布更新事件")
        void whenQuotaUsageExists_shouldReleaseAndPublishUpdate() {
            UserQuotaUsageEntity existingUsage = createTestQuotaUsage(5, 2, 500000000L);
            SubscriptionQuota quota = createTestQuota(10, 5, 1000000000L);
            mockCurrentUser(TEST_TIER_FREE);

            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(existingUsage));
            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);

            userQuotaService.releaseDevices(TEST_USER_ID, 2);

            assertThat(existingUsage.getDeviceCount()).isEqualTo(3);
            verify(userQuotaUsageRepository).save(existingUsage);
            verify(userQuotaSignalPublisher).publishQuotaUpdated(eq(TEST_USER_ID), eq(TEST_TIER_FREE),
                    eq(UserQuotaSignalPublisher.RESOURCE_DEVICES), eq("count"), eq(3L), eq(10L));
        }

        @Test
        @DisplayName("当释放后设备数量为负数时，应设置为0")
        void whenReleaseExceedsCurrent_shouldSetToZero() {
            UserQuotaUsageEntity existingUsage = createTestQuotaUsage(3, 2, 500000000L);
            SubscriptionQuota quota = createTestQuota(10, 5, 1000000000L);
            mockCurrentUser(TEST_TIER_FREE);

            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(existingUsage));
            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);

            userQuotaService.releaseDevices(TEST_USER_ID, 5);

            assertThat(existingUsage.getDeviceCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("当当前设备数量为null时，视为0处理")
        void whenDeviceCountIsNull_shouldTreatAsZero() {
            UserQuotaUsageEntity existingUsage = createTestQuotaUsage(0, 2, 500000000L);
            existingUsage.setDeviceCount(null);
            SubscriptionQuota quota = createTestQuota(10, 5, 1000000000L);
            mockCurrentUser(TEST_TIER_FREE);

            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(existingUsage));
            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);

            userQuotaService.releaseDevices(TEST_USER_ID, 5);

            assertThat(existingUsage.getDeviceCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("当没有认证用户时，发布事件时tier为null")
        void whenNoAuthenticatedUser_shouldPublishWithNullTier() {
            UserQuotaUsageEntity existingUsage = createTestQuotaUsage(5, 2, 500000000L);
            mockNoAuthenticatedUser();

            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(existingUsage));

            userQuotaService.releaseDevices(TEST_USER_ID, 2);

            verify(userQuotaSignalPublisher).publishQuotaUpdated(eq(TEST_USER_ID), eq(null),
                    eq(UserQuotaSignalPublisher.RESOURCE_DEVICES), eq("count"), eq(3L), eq(null));
        }
    }

    @Nested
    @DisplayName("consumeCustomColumns 方法测试")
    class ConsumeCustomColumnsTests {

        @Test
        @DisplayName("当userId为null时，不执行任何操作")
        void whenUserIdIsNull_shouldDoNothing() {
            userQuotaService.consumeCustomColumns(null, TEST_TIER_FREE, 1);

            verify(userQuotaUsageRepository, never()).findByUserId(any(UUID.class));
            verify(subscriptionQuotaFacade, never()).getQuota(anyString());
        }

        @Test
        @DisplayName("当count小于等于0时，不执行任何操作")
        void whenCountIsZeroOrNegative_shouldDoNothing() {
            userQuotaService.consumeCustomColumns(TEST_USER_ID, TEST_TIER_FREE, 0);
            userQuotaService.consumeCustomColumns(TEST_USER_ID, TEST_TIER_FREE, -1);

            verify(userQuotaUsageRepository, never()).findByUserId(any(UUID.class));
        }

        @Test
        @DisplayName("当配额使用记录不存在时，先创建记录再扣减额度")
        void whenQuotaUsageNotExists_shouldCreateFirstThenConsume() {
            SubscriptionQuota quota = createTestQuota(10, 5, 1000000000L);
            UserQuotaUsageEntity existingUsage = createTestQuotaUsage(0, 2, 0L);

            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(existingUsage));
            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);
            when(userQuotaUsageRepository.incrementCustomColumnCountIfWithinLimit(eq(TEST_USER_ID), eq(2), eq(5))).thenReturn(1);

            userQuotaService.consumeCustomColumns(TEST_USER_ID, TEST_TIER_FREE, 2);

            verify(userQuotaUsageRepository).save(any(UserQuotaUsageEntity.class));
            verify(userQuotaUsageRepository).incrementCustomColumnCountIfWithinLimit(TEST_USER_ID, 2, 5);
        }

        @Test
        @DisplayName("当扣减后超出配额时，抛出BizException并发布超限事件")
        void whenExceedsQuota_shouldThrowBizExceptionAndPublishExceeded() {
            SubscriptionQuota quota = createTestQuota(10, 3, 1000000000L);
            UserQuotaUsageEntity existingUsage = createTestQuotaUsage(5, 3, 500000000L);

            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(existingUsage));
            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);
            when(userQuotaUsageRepository.incrementCustomColumnCountIfWithinLimit(eq(TEST_USER_ID), eq(1), eq(3))).thenReturn(0);

            assertThatThrownBy(() -> userQuotaService.consumeCustomColumns(TEST_USER_ID, TEST_TIER_FREE, 1))
                    .isInstanceOf(BizException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.DEVICE_CUSTOM_FIELD_QUOTA_EXCEEDED.getCode());

            verify(userQuotaSignalPublisher).publishQuotaExceeded(eq(TEST_USER_ID), eq(TEST_TIER_FREE),
                    eq(UserQuotaSignalPublisher.RESOURCE_CUSTOM_COLUMNS), eq("count"), eq(4L), eq(3L));
        }

        @Test
        @DisplayName("当配额限制为null时，不检查限制直接扣减")
        void whenLimitIsNull_shouldConsumeWithoutCheck() {
            SubscriptionQuota quota = createTestQuota(10, null, 1000000000L);
            UserQuotaUsageEntity existingUsage = createTestQuotaUsage(5, 10, 500000000L);

            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(existingUsage));
            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);

            userQuotaService.consumeCustomColumns(TEST_USER_ID, TEST_TIER_FREE, 5);

            verify(userQuotaUsageRepository).incrementCustomColumnCount(TEST_USER_ID, 5);
            verify(userQuotaUsageRepository, never()).incrementCustomColumnCountIfWithinLimit(any(), anyInt(), anyInt());
        }
    }

    @Nested
    @DisplayName("releaseCustomColumns 方法测试")
    class ReleaseCustomColumnsTests {

        @Test
        @DisplayName("当userId为null时，不执行任何操作")
        void whenUserIdIsNull_shouldDoNothing() {
            userQuotaService.releaseCustomColumns(null, 1);

            verify(userQuotaUsageRepository, never()).findByUserId(any(UUID.class));
            verify(userQuotaUsageRepository, never()).save(any(UserQuotaUsageEntity.class));
        }

        @Test
        @DisplayName("当count小于等于0时，不执行任何操作")
        void whenCountIsZeroOrNegative_shouldDoNothing() {
            userQuotaService.releaseCustomColumns(TEST_USER_ID, 0);
            userQuotaService.releaseCustomColumns(TEST_USER_ID, -1);

            verify(userQuotaUsageRepository, never()).findByUserId(any(UUID.class));
            verify(userQuotaUsageRepository, never()).save(any(UserQuotaUsageEntity.class));
        }

        @Test
        @DisplayName("当配额使用记录不存在时，不执行任何操作")
        void whenQuotaUsageNotExists_shouldDoNothing() {
            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());

            userQuotaService.releaseCustomColumns(TEST_USER_ID, 1);

            verify(userQuotaUsageRepository, never()).save(any(UserQuotaUsageEntity.class));
        }

        @Test
        @DisplayName("当配额使用记录存在时，释放自定义列额度并发布更新事件")
        void whenQuotaUsageExists_shouldReleaseAndPublishUpdate() {
            UserQuotaUsageEntity existingUsage = createTestQuotaUsage(5, 5, 500000000L);
            SubscriptionQuota quota = createTestQuota(10, 10, 1000000000L);
            mockCurrentUser(TEST_TIER_FREE);

            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(existingUsage));
            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);

            userQuotaService.releaseCustomColumns(TEST_USER_ID, 2);

            assertThat(existingUsage.getCustomColumnCount()).isEqualTo(3);
            verify(userQuotaUsageRepository).save(existingUsage);
            verify(userQuotaSignalPublisher).publishQuotaUpdated(eq(TEST_USER_ID), eq(TEST_TIER_FREE),
                    eq(UserQuotaSignalPublisher.RESOURCE_CUSTOM_COLUMNS), eq("count"), eq(3L), eq(10L));
        }

        @Test
        @DisplayName("当释放后自定义列数量为负数时，应设置为0")
        void whenReleaseExceedsCurrent_shouldSetToZero() {
            UserQuotaUsageEntity existingUsage = createTestQuotaUsage(5, 3, 500000000L);
            SubscriptionQuota quota = createTestQuota(10, 10, 1000000000L);
            mockCurrentUser(TEST_TIER_FREE);

            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(existingUsage));
            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);

            userQuotaService.releaseCustomColumns(TEST_USER_ID, 5);

            assertThat(existingUsage.getCustomColumnCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("syncProgramCount 方法测试")
    class SyncProgramCountTests {

        @Test
        @DisplayName("当userId为null时，不执行任何操作")
        void whenUserIdIsNull_shouldDoNothing() {
            userQuotaService.syncProgramCount(null, 10);

            verify(userQuotaUsageRepository, never()).findByUserId(any(UUID.class));
            verify(userQuotaUsageRepository, never()).setProgramCount(any(UUID.class), anyInt());
        }

        @Test
        @DisplayName("当配额使用记录不存在时，先创建记录再同步")
        void whenQuotaUsageNotExists_shouldCreateFirstThenSync() {
            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());

            userQuotaService.syncProgramCount(TEST_USER_ID, 10);

            verify(userQuotaUsageRepository).save(any(UserQuotaUsageEntity.class));
            verify(userQuotaUsageRepository).setProgramCount(TEST_USER_ID, 10);
        }

        @Test
        @DisplayName("当配额使用记录已存在时，直接同步节目数量")
        void whenQuotaUsageExists_shouldSyncDirectly() {
            UserQuotaUsageEntity existingUsage = createTestQuotaUsage(5, 2, 500000000L);

            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(existingUsage));

            userQuotaService.syncProgramCount(TEST_USER_ID, 15);

            verify(userQuotaUsageRepository, never()).save(any(UserQuotaUsageEntity.class));
            verify(userQuotaUsageRepository).setProgramCount(TEST_USER_ID, 15);
        }

        @Test
        @DisplayName("当programCount为负数时，应同步为0")
        void whenProgramCountIsNegative_shouldSyncAsZero() {
            UserQuotaUsageEntity existingUsage = createTestQuotaUsage(5, 2, 500000000L);

            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(existingUsage));

            userQuotaService.syncProgramCount(TEST_USER_ID, -5);

            verify(userQuotaUsageRepository).setProgramCount(TEST_USER_ID, 0);
        }

        @Test
        @DisplayName("当programCount为0时，应正常同步")
        void whenProgramCountIsZero_shouldSyncNormally() {
            UserQuotaUsageEntity existingUsage = createTestQuotaUsage(5, 2, 500000000L);

            when(userQuotaUsageRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(existingUsage));

            userQuotaService.syncProgramCount(TEST_USER_ID, 0);

            verify(userQuotaUsageRepository).setProgramCount(TEST_USER_ID, 0);
        }
    }
}
