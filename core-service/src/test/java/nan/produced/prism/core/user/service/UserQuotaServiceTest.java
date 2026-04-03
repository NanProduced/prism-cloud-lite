package nan.produced.prism.core.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.system.api.SubscriptionQuota;
import nan.produced.prism.core.system.api.SubscriptionQuotaFacade;
import nan.produced.prism.core.user.domain.UserQuotaUsageEntity;
import nan.produced.prism.core.user.repository.UserQuotaUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class UserQuotaServiceTest {

    @Mock
    private UserQuotaUsageRepository userQuotaUsageRepository;

    @Mock
    private SubscriptionQuotaFacade subscriptionQuotaFacade;

    @Mock
    private UserQuotaSignalPublisher userQuotaSignalPublisher;

    @InjectMocks
    private UserQuotaService userQuotaService;

    private UUID userId;
    private UserQuotaUsageEntity mockQuotaUsage;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userId = UUID.randomUUID();
        mockQuotaUsage = UserQuotaUsageEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .deviceCount(5)
                .customColumnCount(10)
                .lastUpdated(Instant.now())
                .build();
    }

    @Test
    @DisplayName("消耗设备数量 - 成功，未超过配额")
    void testConsumeDevices_Success_WithinLimit() {
        when(userQuotaUsageRepository.findByUserId(userId)).thenReturn(Optional.of(mockQuotaUsage));
        when(subscriptionQuotaFacade.getQuota("PRO")).thenReturn(new SubscriptionQuota(10, 20L, 30, 40, 50));
        when(userQuotaUsageRepository.incrementDeviceCountIfWithinLimit(userId, 2, 10)).thenReturn(1);

        userQuotaService.consumeDevices(userId, "PRO", 2);

        verify(userQuotaUsageRepository, times(1)).incrementDeviceCountIfWithinLimit(userId, 2, 10);
        verify(userQuotaSignalPublisher, times(1)).publishQuotaUpdated(any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("消耗设备数量 - 失败，超过配额")
    void testConsumeDevices_Failure_ExceedLimit() {
        when(userQuotaUsageRepository.findByUserId(userId)).thenReturn(Optional.of(mockQuotaUsage));
        when(subscriptionQuotaFacade.getQuota("PRO")).thenReturn(new SubscriptionQuota(5, 20L, 30, 40, 50));
        when(userQuotaUsageRepository.incrementDeviceCountIfWithinLimit(userId, 2, 5)).thenReturn(0);

        assertThatThrownBy(() -> userQuotaService.consumeDevices(userId, "PRO", 2))
                .isInstanceOf(BizException.class)
                .hasMessageContaining(ErrorCode.DEVICE_QUOTA_EXCEEDED.getMessage());

        verify(userQuotaSignalPublisher, times(1)).publishQuotaExceeded(any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("消耗设备数量 - 无配额限制")
    void testConsumeDevices_NoLimit() {
        when(userQuotaUsageRepository.findByUserId(userId)).thenReturn(Optional.of(mockQuotaUsage));
        when(subscriptionQuotaFacade.getQuota("PRO")).thenReturn(new SubscriptionQuota(null, 20L, 30, 40, 50));

        userQuotaService.consumeDevices(userId, "PRO", 2);

        verify(userQuotaUsageRepository, times(1)).incrementDeviceCount(userId, 2);
        verify(userQuotaSignalPublisher, times(1)).publishQuotaUpdated(any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("消耗设备数量 - 配额使用记录不存在，创建新记录")
    void testConsumeDevices_CreateQuotaUsage() {
        when(userQuotaUsageRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(subscriptionQuotaFacade.getQuota("FREE")).thenReturn(new SubscriptionQuota(10, 20L, 30, 40, 50));
        when(userQuotaUsageRepository.incrementDeviceCountIfWithinLimit(userId, 2, 10)).thenReturn(1);
        when(userQuotaUsageRepository.save(any(UserQuotaUsageEntity.class))).thenReturn(mockQuotaUsage);

        userQuotaService.consumeDevices(userId, "FREE", 2);

        verify(userQuotaUsageRepository, times(1)).save(any(UserQuotaUsageEntity.class));
        verify(userQuotaUsageRepository, times(1)).incrementDeviceCountIfWithinLimit(userId, 2, 10);
    }

    @Test
    @DisplayName("释放设备数量 - 成功")
    void testReleaseDevices_Success() {
        when(userQuotaUsageRepository.findByUserId(userId)).thenReturn(Optional.of(mockQuotaUsage));
        when(userQuotaUsageRepository.save(any(UserQuotaUsageEntity.class))).thenReturn(mockQuotaUsage);

        userQuotaService.releaseDevices(userId, 2);

        verify(userQuotaUsageRepository, times(1)).save(any(UserQuotaUsageEntity.class));
        verify(userQuotaSignalPublisher, times(1)).publishQuotaUpdated(any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("释放设备数量 - 配额使用记录不存在")
    void testReleaseDevices_NoQuotaUsage() {
        when(userQuotaUsageRepository.findByUserId(userId)).thenReturn(Optional.empty());

        userQuotaService.releaseDevices(userId, 2);

        verify(userQuotaUsageRepository, never()).save(any(UserQuotaUsageEntity.class));
        verify(userQuotaSignalPublisher, never()).publishQuotaUpdated(any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("消耗自定义列数量 - 成功，未超过配额")
    void testConsumeCustomColumns_Success_WithinLimit() {
        when(userQuotaUsageRepository.findByUserId(userId)).thenReturn(Optional.of(mockQuotaUsage));
        when(subscriptionQuotaFacade.getQuota("PRO")).thenReturn(new SubscriptionQuota(10, 20L, 30, 40, 50));
        when(userQuotaUsageRepository.incrementCustomColumnCountIfWithinLimit(userId, 5, 50)).thenReturn(1);

        userQuotaService.consumeCustomColumns(userId, "PRO", 5);

        verify(userQuotaUsageRepository, times(1)).incrementCustomColumnCountIfWithinLimit(userId, 5, 50);
        verify(userQuotaSignalPublisher, times(1)).publishQuotaUpdated(any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("消耗自定义列数量 - 失败，超过配额")
    void testConsumeCustomColumns_Failure_ExceedLimit() {
        when(userQuotaUsageRepository.findByUserId(userId)).thenReturn(Optional.of(mockQuotaUsage));
        when(subscriptionQuotaFacade.getQuota("PRO")).thenReturn(new SubscriptionQuota(10, 10L, 30, 40, 50));
        when(userQuotaUsageRepository.incrementCustomColumnCountIfWithinLimit(userId, 5, 10)).thenReturn(0);

        assertThatThrownBy(() -> userQuotaService.consumeCustomColumns(userId, "PRO", 5))
                .isInstanceOf(BizException.class)
                .hasMessageContaining(ErrorCode.DEVICE_CUSTOM_FIELD_QUOTA_EXCEEDED.getMessage());

        verify(userQuotaSignalPublisher, times(1)).publishQuotaExceeded(any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("释放自定义列数量 - 成功")
    void testReleaseCustomColumns_Success() {
        when(userQuotaUsageRepository.findByUserId(userId)).thenReturn(Optional.of(mockQuotaUsage));
        when(userQuotaUsageRepository.save(any(UserQuotaUsageEntity.class))).thenReturn(mockQuotaUsage);

        userQuotaService.releaseCustomColumns(userId, 3);

        verify(userQuotaUsageRepository, times(1)).save(any(UserQuotaUsageEntity.class));
        verify(userQuotaSignalPublisher, times(1)).publishQuotaUpdated(any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("同步程序数量 - 成功")
    void testSyncProgramCount_Success() {
        when(userQuotaUsageRepository.findByUserId(userId)).thenReturn(Optional.of(mockQuotaUsage));

        userQuotaService.syncProgramCount(userId, 5);

        verify(userQuotaUsageRepository, times(1)).setProgramCount(userId, 5);
    }

    @Test
    @DisplayName("同步程序数量 - 配额使用记录不存在，创建新记录")
    void testSyncProgramCount_CreateQuotaUsage() {
        when(userQuotaUsageRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userQuotaUsageRepository.save(any(UserQuotaUsageEntity.class))).thenReturn(mockQuotaUsage);

        userQuotaService.syncProgramCount(userId, 5);

        verify(userQuotaUsageRepository, times(1)).save(any(UserQuotaUsageEntity.class));
        verify(userQuotaUsageRepository, times(1)).setProgramCount(userId, 5);
    }

    @Test
    @DisplayName("消耗设备数量 - 参数无效")
    void testConsumeDevices_InvalidParams() {
        userQuotaService.consumeDevices(null, "PRO", 0);
        userQuotaService.consumeDevices(userId, "PRO", -1);

        verify(userQuotaUsageRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("释放设备数量 - 参数无效")
    void testReleaseDevices_InvalidParams() {
        userQuotaService.releaseDevices(null, 0);
        userQuotaService.releaseDevices(userId, -1);

        verify(userQuotaUsageRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("消耗自定义列数量 - 参数无效")
    void testConsumeCustomColumns_InvalidParams() {
        userQuotaService.consumeCustomColumns(null, "PRO", 0);
        userQuotaService.consumeCustomColumns(userId, "PRO", -1);

        verify(userQuotaUsageRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("释放自定义列数量 - 参数无效")
    void testReleaseCustomColumns_InvalidParams() {
        userQuotaService.releaseCustomColumns(null, 0);
        userQuotaService.releaseCustomColumns(userId, -1);

        verify(userQuotaUsageRepository, never()).findByUserId(any());
    }

    @Test
    @DisplayName("同步程序数量 - 用户ID为空")
    void testSyncProgramCount_NullUserId() {
        userQuotaService.syncProgramCount(null, 5);

        verify(userQuotaUsageRepository, never()).findByUserId(any());
    }
}
