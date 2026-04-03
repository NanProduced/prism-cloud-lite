package nan.produced.prism.core.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import nan.produced.prism.core.system.api.SubscriptionQuota;
import nan.produced.prism.core.system.api.SubscriptionQuotaFacade;
import nan.produced.prism.core.user.api.StorageFileType;
import nan.produced.prism.core.user.api.StorageSourceType;
import nan.produced.prism.core.user.domain.UserQuotaUsageEntity;
import nan.produced.prism.core.user.domain.storage.UserStorageUsageEntity;
import nan.produced.prism.core.user.repository.UserQuotaUsageRepository;
import nan.produced.prism.core.user.repository.UserStorageUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class UserQuotaQueryServiceTest {

    @Mock
    private UserQuotaUsageRepository userQuotaUsageRepository;

    @Mock
    private UserStorageUsageRepository userStorageUsageRepository;

    @Mock
    private SubscriptionQuotaFacade subscriptionQuotaFacade;

    @InjectMocks
    private UserQuotaQueryService userQuotaQueryService;

    private UUID userId;
    private UserQuotaUsageEntity mockQuotaUsage;
    private UserStorageUsageEntity mockStorageUsage;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userId = UUID.randomUUID();
        mockQuotaUsage = UserQuotaUsageEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .deviceCount(5)
                .programCount(10)
                .customColumnCount(15)
                .storageTotalBytes(1024L * 1024 * 1024) // 1GB
                .lastUpdated(Instant.now())
                .build();
        mockStorageUsage = UserStorageUsageEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .sourceType(StorageSourceType.MEDIA_LIBRARY)
                .fileType(StorageFileType.IMAGE)
                .fileCount(10)
                .totalBytes(512L * 1024 * 1024) // 512MB
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("获取存储配额 - 成功，有配额使用记录")
    void testGetStorageQuota_Success_WithUsage() {
        when(subscriptionQuotaFacade.getQuota("PRO")).thenReturn(new SubscriptionQuota(10, 5L * 1024 * 1024 * 1024, 20, 100, 20));
        when(userQuotaUsageRepository.findByUserId(userId)).thenReturn(java.util.Optional.of(mockQuotaUsage));

        var result = userQuotaQueryService.getStorageQuota(userId, "PRO");

        assertThat(result).isNotNull();
        assertThat(result.tier()).isEqualTo("PRO");
        assertThat(result.quotaBytes()).isEqualTo(5L * 1024 * 1024 * 1024);
        assertThat(result.usedBytes()).isEqualTo(1024L * 1024 * 1024);
        assertThat(result.availableBytes()).isEqualTo(4L * 1024 * 1024 * 1024);
        assertThat(result.percent()).isNotNull();
    }

    @Test
    @DisplayName("获取存储配额 - 成功，无配额使用记录")
    void testGetStorageQuota_Success_NoUsage() {
        when(subscriptionQuotaFacade.getQuota("PRO")).thenReturn(new SubscriptionQuota(10, 5L * 1024 * 1024 * 1024, 20, 100, 20));
        when(userQuotaUsageRepository.findByUserId(userId)).thenReturn(java.util.Optional.empty());
        when(userStorageUsageRepository.findByUserId(userId)).thenReturn(List.of(mockStorageUsage));

        var result = userQuotaQueryService.getStorageQuota(userId, "PRO");

        assertThat(result).isNotNull();
        assertThat(result.tier()).isEqualTo("PRO");
        assertThat(result.quotaBytes()).isEqualTo(5L * 1024 * 1024 * 1024);
        assertThat(result.usedBytes()).isEqualTo(512L * 1024 * 1024);
        assertThat(result.availableBytes()).isEqualTo(4831838208L); // 5GB - 512MB
    }

    @Test
    @DisplayName("获取存储配额 - 无用户ID")
    void testGetStorageQuota_NoUserId() {
        when(subscriptionQuotaFacade.getQuota("FREE")).thenReturn(new SubscriptionQuota(5, 2L * 1024 * 1024 * 1024, 10, 50, 10));

        var result = userQuotaQueryService.getStorageQuota(null, "FREE");

        assertThat(result).isNotNull();
        assertThat(result.tier()).isEqualTo("FREE");
        assertThat(result.quotaBytes()).isEqualTo(2L * 1024 * 1024 * 1024);
        assertThat(result.usedBytes()).isEqualTo(0L);
        assertThat(result.availableBytes()).isEqualTo(2L * 1024 * 1024 * 1024);
    }

    @Test
    @DisplayName("获取配额概览 - 成功")
    void testGetQuotaOverview_Success() {
        when(subscriptionQuotaFacade.getQuota("PRO")).thenReturn(new SubscriptionQuota(10, 5L * 1024 * 1024 * 1024, 20, 100, 20));
        when(userQuotaUsageRepository.findByUserId(userId)).thenReturn(java.util.Optional.of(mockQuotaUsage));

        var result = userQuotaQueryService.getQuotaOverview(userId, "PRO");

        assertThat(result).isNotNull();
        assertThat(result.tier()).isEqualTo("PRO");
        assertThat(result.metrics()).hasSize(5);
    }

    @Test
    @DisplayName("获取配额概览 - 无用户ID")
    void testGetQuotaOverview_NoUserId() {
        when(subscriptionQuotaFacade.getQuota("FREE")).thenReturn(new SubscriptionQuota(5, 2L * 1024 * 1024 * 1024, 10, 50, 10));

        var result = userQuotaQueryService.getQuotaOverview(null, "FREE");

        assertThat(result).isNotNull();
        assertThat(result.tier()).isEqualTo("FREE");
        assertThat(result.metrics()).hasSize(5);
    }

    @Test
    @DisplayName("获取存储 ledger - 成功")
    void testGetStorageLedger_Success() {
        when(subscriptionQuotaFacade.getQuota("PRO")).thenReturn(new SubscriptionQuota(10, 5L * 1024 * 1024 * 1024, 20, 100, 20));
        when(userQuotaUsageRepository.findByUserId(userId)).thenReturn(java.util.Optional.of(mockQuotaUsage));
        when(userStorageUsageRepository.findByUserId(userId)).thenReturn(List.of(mockStorageUsage));

        var result = userQuotaQueryService.getStorageLedger(userId, "PRO");

        assertThat(result).isNotNull();
        assertThat(result.tier()).isEqualTo("PRO");
        assertThat(result.quotaBytes()).isEqualTo(5L * 1024 * 1024 * 1024);
        assertThat(result.ledgerTotalBytes()).isEqualTo(512L * 1024 * 1024);
        assertThat(result.quotaUsedBytes()).isEqualTo(1024L * 1024 * 1024);
        assertThat(result.sources()).hasSize(1);
    }

    @Test
    @DisplayName("获取存储 ledger - 无用户ID")
    void testGetStorageLedger_NoUserId() {
        when(subscriptionQuotaFacade.getQuota("FREE")).thenReturn(new SubscriptionQuota(5, 2L * 1024 * 1024 * 1024, 10, 50, 10));

        var result = userQuotaQueryService.getStorageLedger(null, "FREE");

        assertThat(result).isNotNull();
        assertThat(result.tier()).isEqualTo("FREE");
        assertThat(result.quotaBytes()).isEqualTo(2L * 1024 * 1024 * 1024);
        assertThat(result.ledgerTotalBytes()).isEqualTo(0L);
        assertThat(result.quotaUsedBytes()).isEqualTo(0L);
        assertThat(result.sources()).isEmpty();
    }

    @Test
    @DisplayName("获取存储 ledger - 无存储使用记录")
    void testGetStorageLedger_NoStorageUsage() {
        when(subscriptionQuotaFacade.getQuota("PRO")).thenReturn(new SubscriptionQuota(10, 5L * 1024 * 1024 * 1024, 20, 100, 20));
        when(userQuotaUsageRepository.findByUserId(userId)).thenReturn(java.util.Optional.of(mockQuotaUsage));
        when(userStorageUsageRepository.findByUserId(userId)).thenReturn(List.of());

        var result = userQuotaQueryService.getStorageLedger(userId, "PRO");

        assertThat(result).isNotNull();
        assertThat(result.tier()).isEqualTo("PRO");
        assertThat(result.ledgerTotalBytes()).isEqualTo(0L);
        assertThat(result.quotaUsedBytes()).isEqualTo(1024L * 1024 * 1024);
        assertThat(result.sources()).isEmpty();
    }
}
