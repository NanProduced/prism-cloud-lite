package nan.produced.prism.core.user.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.core.user.api.StorageFileType;
import nan.produced.prism.core.user.api.StorageSourceType;
import nan.produced.prism.core.user.domain.UserQuotaUsageEntity;
import nan.produced.prism.core.user.domain.storage.UserStorageUsageEntity;
import nan.produced.prism.core.user.repository.UserQuotaUsageRepository;
import nan.produced.prism.core.user.repository.UserStorageUsageRepository;
import nan.produced.prism.core.system.api.SubscriptionQuota;
import nan.produced.prism.core.system.api.SubscriptionQuotaFacade;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class UserStorageUsageServiceTest {

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

    private UUID userId;
    private UserQuotaUsageEntity mockQuotaUsage;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userId = UUID.randomUUID();
        mockQuotaUsage = UserQuotaUsageEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .storageTotalBytes(1024L)
                .build();
    }

    @Test
    @DisplayName("增加存储使用量 - 成功，已存在记录")
    void testIncrementUsage_Success_ExistingRecord() {
        when(userStorageUsageRepository.incrementUsage(userId, StorageSourceType.MEDIA_LIBRARY, StorageFileType.IMAGE, 2, 1024L)).thenReturn(1);
        when(userQuotaUsageRepository.findByUserId(userId)).thenReturn(Optional.of(mockQuotaUsage));

        userStorageUsageService.incrementUsage(userId, StorageSourceType.MEDIA_LIBRARY, StorageFileType.IMAGE, 2, 1024L);

        verify(userStorageUsageRepository, times(1)).incrementUsage(userId, StorageSourceType.MEDIA_LIBRARY, StorageFileType.IMAGE, 2, 1024L);
        verify(userQuotaUsageRepository, times(1)).incrementStorageTotalBytes(userId, 1024L);
        verify(userQuotaSignalPublisher, times(1)).publishQuotaUpdated(any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("增加存储使用量 - 成功，创建新记录")
    void testIncrementUsage_Success_CreateRecord() {
        when(userStorageUsageRepository.incrementUsage(userId, StorageSourceType.MEDIA_LIBRARY, StorageFileType.IMAGE, 2, 1024L)).thenReturn(0);
        when(userStorageUsageRepository.save(any(UserStorageUsageEntity.class))).thenReturn(mock(UserStorageUsageEntity.class));
        when(userQuotaUsageRepository.findByUserId(userId)).thenReturn(Optional.of(mockQuotaUsage));

        userStorageUsageService.incrementUsage(userId, StorageSourceType.MEDIA_LIBRARY, StorageFileType.IMAGE, 2, 1024L);

        verify(userStorageUsageRepository, times(1)).save(any(UserStorageUsageEntity.class));
        verify(userQuotaUsageRepository, times(1)).incrementStorageTotalBytes(userId, 1024L);
        verify(userQuotaSignalPublisher, times(1)).publishQuotaUpdated(any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("增加存储使用量 - 参数无效")
    void testIncrementUsage_InvalidParams() {
        // 测试空参数
        userStorageUsageService.incrementUsage(null, StorageSourceType.MEDIA_LIBRARY, StorageFileType.IMAGE, 2, 1024L);
        userStorageUsageService.incrementUsage(userId, null, StorageFileType.IMAGE, 2, 1024L);
        userStorageUsageService.incrementUsage(userId, StorageSourceType.MEDIA_LIBRARY, null, 2, 1024L);
        // 测试无效数量
        userStorageUsageService.incrementUsage(userId, StorageSourceType.MEDIA_LIBRARY, StorageFileType.IMAGE, -1, -1024L);
        userStorageUsageService.incrementUsage(userId, StorageSourceType.MEDIA_LIBRARY, StorageFileType.IMAGE, 0, 0L);

        verify(userStorageUsageRepository, never()).incrementUsage(any(), any(), any(), anyInt(), anyLong());
        verify(userStorageUsageRepository, never()).save(any());
    }

    @Test
    @DisplayName("减少存储使用量 - 成功")
    void testDecrementUsage_Success() {
        when(userStorageUsageRepository.decrementUsage(userId, StorageSourceType.MEDIA_LIBRARY, StorageFileType.IMAGE, 1, 512L)).thenReturn(1);
        when(userQuotaUsageRepository.findByUserId(userId)).thenReturn(Optional.of(mockQuotaUsage));

        userStorageUsageService.decrementUsage(userId, StorageSourceType.MEDIA_LIBRARY, StorageFileType.IMAGE, 1, 512L);

        verify(userStorageUsageRepository, times(1)).decrementUsage(userId, StorageSourceType.MEDIA_LIBRARY, StorageFileType.IMAGE, 1, 512L);
        verify(userQuotaUsageRepository, times(1)).incrementStorageTotalBytes(userId, -512L);
        verify(userQuotaSignalPublisher, times(1)).publishQuotaUpdated(any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("减少存储使用量 - 无记录")
    void testDecrementUsage_NoRecord() {
        when(userStorageUsageRepository.decrementUsage(userId, StorageSourceType.MEDIA_LIBRARY, StorageFileType.IMAGE, 1, 512L)).thenReturn(0);

        userStorageUsageService.decrementUsage(userId, StorageSourceType.MEDIA_LIBRARY, StorageFileType.IMAGE, 1, 512L);

        verify(userStorageUsageRepository, times(1)).decrementUsage(userId, StorageSourceType.MEDIA_LIBRARY, StorageFileType.IMAGE, 1, 512L);
        verify(userQuotaUsageRepository, never()).incrementStorageTotalBytes(any(), anyLong());
        verify(userQuotaSignalPublisher, never()).publishQuotaUpdated(any(), any(), any(), any(), anyLong(), any());
    }

    @Test
    @DisplayName("减少存储使用量 - 参数无效")
    void testDecrementUsage_InvalidParams() {
        // 测试空参数
        userStorageUsageService.decrementUsage(null, StorageSourceType.MEDIA_LIBRARY, StorageFileType.IMAGE, 1, 512L);
        userStorageUsageService.decrementUsage(userId, null, StorageFileType.IMAGE, 1, 512L);
        userStorageUsageService.decrementUsage(userId, StorageSourceType.MEDIA_LIBRARY, null, 1, 512L);
        // 测试无效数量
        userStorageUsageService.decrementUsage(userId, StorageSourceType.MEDIA_LIBRARY, StorageFileType.IMAGE, -1, -512L);
        userStorageUsageService.decrementUsage(userId, StorageSourceType.MEDIA_LIBRARY, StorageFileType.IMAGE, 0, 0L);

        verify(userStorageUsageRepository, never()).decrementUsage(any(), any(), any(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("发布存储配额更新 - 无配额使用记录")
    void testPublishStorageQuotaUpdated_NoQuotaUsage() {
        when(userQuotaUsageRepository.findByUserId(userId)).thenReturn(Optional.empty());

        userStorageUsageService.incrementUsage(userId, StorageSourceType.MEDIA_LIBRARY, StorageFileType.IMAGE, 1, 512L);

        verify(userQuotaSignalPublisher, never()).publishQuotaUpdated(any(), any(), any(), any(), anyLong(), any());
    }
}
