package nan.produced.prism.core.user.service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.core.system.api.SubscriptionQuota;
import nan.produced.prism.core.system.api.SubscriptionQuotaFacade;
import nan.produced.prism.core.user.api.StorageFileType;
import nan.produced.prism.core.user.api.StorageSourceType;
import nan.produced.prism.core.user.domain.UserQuotaUsageEntity;
import nan.produced.prism.core.user.domain.storage.UserStorageUsageEntity;
import nan.produced.prism.core.user.dto.UserQuotaMetricView;
import nan.produced.prism.core.user.dto.UserQuotaOverviewView;
import nan.produced.prism.core.user.dto.UserStorageLedgerView;
import nan.produced.prism.core.user.dto.UserStorageQuotaView;
import nan.produced.prism.core.user.repository.UserQuotaUsageRepository;
import nan.produced.prism.core.user.repository.UserStorageUsageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserQuotaQueryService 单元测试")
class UserQuotaQueryServiceTests {

    @Mock
    private UserQuotaUsageRepository userQuotaUsageRepository;

    @Mock
    private UserStorageUsageRepository userStorageUsageRepository;

    @Mock
    private SubscriptionQuotaFacade subscriptionQuotaFacade;

    @InjectMocks
    private UserQuotaQueryService userQuotaQueryService;

    private static final UUID TEST_USER_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final String TEST_TIER_FREE = "FREE";
    private static final String TEST_TIER_PRO = "PRO";

    private SubscriptionQuota createTestQuota(Integer deviceLimit, Integer programLimit, Integer customColumnLimit, Long storageLimit) {
        return new SubscriptionQuota(deviceLimit, storageLimit, programLimit, 10, customColumnLimit);
    }

    private UserQuotaUsageEntity createTestQuotaUsage(int deviceCount, int programCount, int customColumnCount, long storageBytes) {
        return UserQuotaUsageEntity.builder()
                .id(UUID.randomUUID())
                .userId(TEST_USER_ID)
                .deviceCount(deviceCount)
                .programCount(programCount)
                .customColumnCount(customColumnCount)
                .storageTotalBytes(storageBytes)
                .lastUpdated(Instant.now())
                .version(0)
                .build();
    }

    private UserStorageUsageEntity createTestStorageUsage(StorageSourceType sourceType, StorageFileType fileType, int fileCount, long totalBytes) {
        return UserStorageUsageEntity.builder()
                .id(UUID.randomUUID())
                .userId(TEST_USER_ID)
                .sourceType(sourceType)
                .fileType(fileType)
                .fileCount(fileCount)
                .totalBytes(totalBytes)
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("getStorageQuota 方法测试")
    class GetStorageQuotaTests {

        @Test
        @DisplayName("当userId和usage都存在时，正确计算存储配额")
        void whenUserIdAndUsageExist_shouldCalculateCorrectly() {
            SubscriptionQuota quota = createTestQuota(10, 100, 5, 1000000000L);
            UserQuotaUsageEntity usage = createTestQuotaUsage(3, 10, 2, 500000000L);

            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);
            when(userQuotaUsageRepository.findByUserId(eq(TEST_USER_ID))).thenReturn(Optional.of(usage));

            UserStorageQuotaView result = userQuotaQueryService.getStorageQuota(TEST_USER_ID, TEST_TIER_FREE);

            assertThat(result).isNotNull();
            assertThat(result.tier()).isEqualTo(TEST_TIER_FREE);
            assertThat(result.quotaBytes()).isEqualTo(1000000000L);
            assertThat(result.usedBytes()).isEqualTo(500000000L);
            assertThat(result.availableBytes()).isEqualTo(500000000L);
            assertThat(result.percent()).isEqualTo(0.5);
            assertThat(result.usageUpdatedAt()).isEqualTo(usage.getLastUpdated());
        }

        @Test
        @DisplayName("当userId为null时，不查询usage")
        void whenUserIdIsNull_shouldNotQueryUsage() {
            SubscriptionQuota quota = createTestQuota(10, 100, 5, 1000000000L);

            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);

            UserStorageQuotaView result = userQuotaQueryService.getStorageQuota(null, TEST_TIER_FREE);

            assertThat(result).isNotNull();
            assertThat(result.usedBytes()).isEqualTo(0L);
            verify(userQuotaUsageRepository, never()).findByUserId(any());
        }

        @Test
        @DisplayName("当usage不存在但userId存在时，从ledger计算usedBytes")
        void whenUsageNotExistsButUserIdExists_shouldCalculateFromLedger() {
            SubscriptionQuota quota = createTestQuota(10, 100, 5, 1000000000L);
            List<UserStorageUsageEntity> ledgerRows = List.of(
                    createTestStorageUsage(StorageSourceType.MEDIA_LIBRARY, StorageFileType.IMAGE, 10, 100000L),
                    createTestStorageUsage(StorageSourceType.MEDIA_LIBRARY, StorageFileType.VIDEO, 5, 400000L)
            );

            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);
            when(userQuotaUsageRepository.findByUserId(eq(TEST_USER_ID))).thenReturn(Optional.empty());
            when(userStorageUsageRepository.findByUserId(eq(TEST_USER_ID))).thenReturn(ledgerRows);

            UserStorageQuotaView result = userQuotaQueryService.getStorageQuota(TEST_USER_ID, TEST_TIER_FREE);

            assertThat(result).isNotNull();
            assertThat(result.usedBytes()).isEqualTo(500000L);
        }

        @Test
        @DisplayName("当quota为null时，quotaBytes为null")
        void whenQuotaIsNull_shouldReturnNullQuotaBytes() {
            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(null);

            UserStorageQuotaView result = userQuotaQueryService.getStorageQuota(TEST_USER_ID, TEST_TIER_FREE);

            assertThat(result).isNotNull();
            assertThat(result.quotaBytes()).isNull();
            assertThat(result.availableBytes()).isNull();
            assertThat(result.percent()).isNull();
        }

        @Test
        @DisplayName("当storageLimitBytes为null时，quotaBytes为null")
        void whenStorageLimitIsNull_shouldReturnNullQuotaBytes() {
            SubscriptionQuota quota = createTestQuota(10, 100, 5, null);

            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);

            UserStorageQuotaView result = userQuotaQueryService.getStorageQuota(TEST_USER_ID, TEST_TIER_FREE);

            assertThat(result).isNotNull();
            assertThat(result.quotaBytes()).isNull();
            assertThat(result.availableBytes()).isNull();
            assertThat(result.percent()).isNull();
        }

        @Test
        @DisplayName("当storageLimitBytes为-1时，quotaBytes为-1，availableBytes和percent为null（无限制）")
        void whenStorageLimitIsNegativeOne_shouldReturnNullAvailableAndPercent() {
            SubscriptionQuota quota = createTestQuota(10, 100, 5, -1L);

            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);

            UserStorageQuotaView result = userQuotaQueryService.getStorageQuota(TEST_USER_ID, TEST_TIER_FREE);

            assertThat(result).isNotNull();
            assertThat(result.quotaBytes()).isEqualTo(-1L);
            assertThat(result.availableBytes()).isNull();
            assertThat(result.percent()).isNull();
        }

        @Test
        @DisplayName("当tier为null时，使用FREE作为默认值")
        void whenTierIsNull_shouldUseFreeAsDefault() {
            SubscriptionQuota quota = createTestQuota(10, 100, 5, 1000000000L);

            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);

            UserStorageQuotaView result = userQuotaQueryService.getStorageQuota(TEST_USER_ID, null);

            assertThat(result.tier()).isEqualTo(TEST_TIER_FREE);
            verify(subscriptionQuotaFacade).getQuota(TEST_TIER_FREE);
        }

        @Test
        @DisplayName("当tier为pro时，转换为PRO")
        void whenTierIsPro_shouldNormalizeToPro() {
            SubscriptionQuota quota = createTestQuota(100, 1000, 50, 10000000000L);

            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_PRO))).thenReturn(quota);

            UserStorageQuotaView result = userQuotaQueryService.getStorageQuota(TEST_USER_ID, "pro");

            assertThat(result.tier()).isEqualTo(TEST_TIER_PRO);
            verify(subscriptionQuotaFacade).getQuota(TEST_TIER_PRO);
        }

        @Test
        @DisplayName("当tier为其他值时，使用FREE")
        void whenTierIsOther_shouldUseFree() {
            SubscriptionQuota quota = createTestQuota(10, 100, 5, 1000000000L);

            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);

            UserStorageQuotaView result = userQuotaQueryService.getStorageQuota(TEST_USER_ID, "ENTERPRISE");

            assertThat(result.tier()).isEqualTo(TEST_TIER_FREE);
            verify(subscriptionQuotaFacade).getQuota(TEST_TIER_FREE);
        }

        @Test
        @DisplayName("当usedBytes超过quota时，availableBytes为0")
        void whenUsedExceedsQuota_shouldReturnZeroAvailable() {
            SubscriptionQuota quota = createTestQuota(10, 100, 5, 1000000000L);
            UserQuotaUsageEntity usage = createTestQuotaUsage(3, 10, 2, 1500000000L);

            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);
            when(userQuotaUsageRepository.findByUserId(eq(TEST_USER_ID))).thenReturn(Optional.of(usage));

            UserStorageQuotaView result = userQuotaQueryService.getStorageQuota(TEST_USER_ID, TEST_TIER_FREE);

            assertThat(result.availableBytes()).isEqualTo(0L);
            assertThat(result.percent()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("当storageTotalBytes为null时，usedBytes为0")
        void whenStorageTotalBytesIsNull_shouldReturnZeroUsed() {
            SubscriptionQuota quota = createTestQuota(10, 100, 5, 1000000000L);
            UserQuotaUsageEntity usage = createTestQuotaUsage(3, 10, 2, 0L);
            usage.setStorageTotalBytes(null);

            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);
            when(userQuotaUsageRepository.findByUserId(eq(TEST_USER_ID))).thenReturn(Optional.of(usage));

            UserStorageQuotaView result = userQuotaQueryService.getStorageQuota(TEST_USER_ID, TEST_TIER_FREE);

            assertThat(result.usedBytes()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("getQuotaOverview 方法测试")
    class GetQuotaOverviewTests {

        @Test
        @DisplayName("当userId和usage都存在时，正确构建配额概览")
        void whenUserIdAndUsageExist_shouldBuildOverviewCorrectly() {
            SubscriptionQuota quota = createTestQuota(10, 100, 5, 1000000000L);
            UserQuotaUsageEntity usage = createTestQuotaUsage(3, 10, 2, 500000000L);

            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);
            when(userQuotaUsageRepository.findByUserId(eq(TEST_USER_ID))).thenReturn(Optional.of(usage));

            UserQuotaOverviewView result = userQuotaQueryService.getQuotaOverview(TEST_USER_ID, TEST_TIER_FREE);

            assertThat(result).isNotNull();
            assertThat(result.tier()).isEqualTo(TEST_TIER_FREE);
            assertThat(result.metrics()).hasSize(5);

            UserQuotaMetricView devicesMetric = findMetric(result.metrics(), "devices");
            assertThat(devicesMetric).isNotNull();
            assertThat(devicesMetric.used()).isEqualTo(3L);
            assertThat(devicesMetric.limit()).isEqualTo(10L);
            assertThat(devicesMetric.percent()).isEqualTo(0.3);

            UserQuotaMetricView programsMetric = findMetric(result.metrics(), "programs");
            assertThat(programsMetric).isNotNull();
            assertThat(programsMetric.used()).isEqualTo(10L);
            assertThat(programsMetric.limit()).isEqualTo(100L);

            UserQuotaMetricView customColumnsMetric = findMetric(result.metrics(), "customColumns");
            assertThat(customColumnsMetric).isNotNull();
            assertThat(customColumnsMetric.used()).isEqualTo(2L);
            assertThat(customColumnsMetric.limit()).isEqualTo(5L);

            UserQuotaMetricView storageMetric = findMetric(result.metrics(), "storageBytes");
            assertThat(storageMetric).isNotNull();
            assertThat(storageMetric.used()).isEqualTo(500000000L);
            assertThat(storageMetric.limit()).isEqualTo(1000000000L);

            UserQuotaMetricView programVersionsMetric = findMetric(result.metrics(), "programVersionsPerProgram");
            assertThat(programVersionsMetric).isNotNull();
            assertThat(programVersionsMetric.used()).isEqualTo(0L);
            assertThat(programVersionsMetric.limit()).isEqualTo(10L);
        }

        @Test
        @DisplayName("当userId为null时，used值为0")
        void whenUserIdIsNull_shouldReturnZeroUsed() {
            SubscriptionQuota quota = createTestQuota(10, 100, 5, 1000000000L);

            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);

            UserQuotaOverviewView result = userQuotaQueryService.getQuotaOverview(null, TEST_TIER_FREE);

            assertThat(result).isNotNull();
            assertThat(findMetric(result.metrics(), "devices").used()).isEqualTo(0L);
            assertThat(findMetric(result.metrics(), "programs").used()).isEqualTo(0L);
            assertThat(findMetric(result.metrics(), "customColumns").used()).isEqualTo(0L);
            assertThat(findMetric(result.metrics(), "storageBytes").used()).isEqualTo(0L);
        }

        @Test
        @DisplayName("当usage不存在时，used值为0")
        void whenUsageNotExists_shouldReturnZeroUsed() {
            SubscriptionQuota quota = createTestQuota(10, 100, 5, 1000000000L);

            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);
            when(userQuotaUsageRepository.findByUserId(eq(TEST_USER_ID))).thenReturn(Optional.empty());

            UserQuotaOverviewView result = userQuotaQueryService.getQuotaOverview(TEST_USER_ID, TEST_TIER_FREE);

            assertThat(result).isNotNull();
            assertThat(findMetric(result.metrics(), "devices").used()).isEqualTo(0L);
            assertThat(findMetric(result.metrics(), "programs").used()).isEqualTo(0L);
            assertThat(findMetric(result.metrics(), "customColumns").used()).isEqualTo(0L);
            assertThat(findMetric(result.metrics(), "storageBytes").used()).isEqualTo(0L);
        }

        @Test
        @DisplayName("当quota为null时，limit为null")
        void whenQuotaIsNull_shouldReturnNullLimit() {
            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(null);

            UserQuotaOverviewView result = userQuotaQueryService.getQuotaOverview(TEST_USER_ID, TEST_TIER_FREE);

            assertThat(result).isNotNull();
            assertThat(findMetric(result.metrics(), "devices").limit()).isNull();
            assertThat(findMetric(result.metrics(), "programs").limit()).isNull();
            assertThat(findMetric(result.metrics(), "customColumns").limit()).isNull();
            assertThat(findMetric(result.metrics(), "storageBytes").limit()).isNull();
            assertThat(findMetric(result.metrics(), "programVersionsPerProgram").limit()).isNull();
        }

        @Test
        @DisplayName("当usage中的count为null时，used值为0")
        void whenUsageCountIsNull_shouldReturnZeroUsed() {
            SubscriptionQuota quota = createTestQuota(10, 100, 5, 1000000000L);
            UserQuotaUsageEntity usage = createTestQuotaUsage(0, 0, 0, 0L);
            usage.setDeviceCount(null);
            usage.setProgramCount(null);
            usage.setCustomColumnCount(null);
            usage.setStorageTotalBytes(null);

            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);
            when(userQuotaUsageRepository.findByUserId(eq(TEST_USER_ID))).thenReturn(Optional.of(usage));

            UserQuotaOverviewView result = userQuotaQueryService.getQuotaOverview(TEST_USER_ID, TEST_TIER_FREE);

            assertThat(findMetric(result.metrics(), "devices").used()).isEqualTo(0L);
            assertThat(findMetric(result.metrics(), "programs").used()).isEqualTo(0L);
            assertThat(findMetric(result.metrics(), "customColumns").used()).isEqualTo(0L);
            assertThat(findMetric(result.metrics(), "storageBytes").used()).isEqualTo(0L);
        }

        @Test
        @DisplayName("当tier为null时，使用FREE作为默认值")
        void whenTierIsNull_shouldUseFreeAsDefault() {
            SubscriptionQuota quota = createTestQuota(10, 100, 5, 1000000000L);

            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);

            UserQuotaOverviewView result = userQuotaQueryService.getQuotaOverview(TEST_USER_ID, null);

            assertThat(result.tier()).isEqualTo(TEST_TIER_FREE);
            verify(subscriptionQuotaFacade).getQuota(TEST_TIER_FREE);
        }

        private UserQuotaMetricView findMetric(List<UserQuotaMetricView> metrics, String resource) {
            return metrics.stream()
                    .filter(m -> resource.equals(m.resource()))
                    .findFirst()
                    .orElse(null);
        }
    }

    @Nested
    @DisplayName("getStorageLedger 方法测试")
    class GetStorageLedgerTests {

        @Test
        @DisplayName("当userId和ledger都存在时，正确构建对账明细")
        void whenUserIdAndLedgerExist_shouldBuildLedgerCorrectly() {
            SubscriptionQuota quota = createTestQuota(10, 100, 5, 1000000000L);
            UserQuotaUsageEntity usage = createTestQuotaUsage(3, 10, 2, 600000L);
            List<UserStorageUsageEntity> ledgerRows = List.of(
                    createTestStorageUsage(StorageSourceType.MEDIA_LIBRARY, StorageFileType.IMAGE, 10, 100000L),
                    createTestStorageUsage(StorageSourceType.MEDIA_LIBRARY, StorageFileType.VIDEO, 5, 400000L),
                    createTestStorageUsage(StorageSourceType.SCREENSHOT, StorageFileType.IMAGE, 20, 100000L)
            );

            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);
            when(userQuotaUsageRepository.findByUserId(eq(TEST_USER_ID))).thenReturn(Optional.of(usage));
            when(userStorageUsageRepository.findByUserId(eq(TEST_USER_ID))).thenReturn(ledgerRows);

            UserStorageLedgerView result = userQuotaQueryService.getStorageLedger(TEST_USER_ID, TEST_TIER_FREE);

            assertThat(result).isNotNull();
            assertThat(result.tier()).isEqualTo(TEST_TIER_FREE);
            assertThat(result.quotaBytes()).isEqualTo(1000000000L);
            assertThat(result.ledgerTotalBytes()).isEqualTo(600000L);
            assertThat(result.quotaUsedBytes()).isEqualTo(600000L);
            assertThat(result.mismatchBytes()).isEqualTo(0L);
            assertThat(result.sources()).hasSize(2);

            assertThat(result.sources().get(0).sourceType()).isEqualTo(StorageSourceType.MEDIA_LIBRARY);
            assertThat(result.sources().get(0).totalBytes()).isEqualTo(500000L);
            assertThat(result.sources().get(0).totalCount()).isEqualTo(15);
            assertThat(result.sources().get(0).items()).hasSize(2);

            assertThat(result.sources().get(1).sourceType()).isEqualTo(StorageSourceType.SCREENSHOT);
            assertThat(result.sources().get(1).totalBytes()).isEqualTo(100000L);
            assertThat(result.sources().get(1).totalCount()).isEqualTo(20);
        }

        @Test
        @DisplayName("当userId为null时，返回空的ledger")
        void whenUserIdIsNull_shouldReturnEmptyLedger() {
            SubscriptionQuota quota = createTestQuota(10, 100, 5, 1000000000L);

            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);

            UserStorageLedgerView result = userQuotaQueryService.getStorageLedger(null, TEST_TIER_FREE);

            assertThat(result).isNotNull();
            assertThat(result.ledgerTotalBytes()).isEqualTo(0L);
            assertThat(result.quotaUsedBytes()).isEqualTo(0L);
            assertThat(result.sources()).isEmpty();
            verify(userStorageUsageRepository, never()).findByUserId(any());
        }

        @Test
        @DisplayName("当ledger为空时，返回空的sources")
        void whenLedgerIsEmpty_shouldReturnEmptySources() {
            SubscriptionQuota quota = createTestQuota(10, 100, 5, 1000000000L);
            UserQuotaUsageEntity usage = createTestQuotaUsage(3, 10, 2, 0L);

            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);
            when(userQuotaUsageRepository.findByUserId(eq(TEST_USER_ID))).thenReturn(Optional.of(usage));
            when(userStorageUsageRepository.findByUserId(eq(TEST_USER_ID))).thenReturn(List.of());

            UserStorageLedgerView result = userQuotaQueryService.getStorageLedger(TEST_USER_ID, TEST_TIER_FREE);

            assertThat(result.sources()).isEmpty();
            assertThat(result.ledgerTotalBytes()).isEqualTo(0L);
        }

        @Test
        @DisplayName("当ledger中的row为null时，跳过该row")
        void whenLedgerRowIsNull_shouldSkip() {
            SubscriptionQuota quota = createTestQuota(10, 100, 5, 1000000000L);
            UserQuotaUsageEntity usage = createTestQuotaUsage(3, 10, 2, 100000L);
            List<UserStorageUsageEntity> ledgerRows = Arrays.asList(
                    null,
                    createTestStorageUsage(StorageSourceType.MEDIA_LIBRARY, StorageFileType.IMAGE, 10, 100000L),
                    null
            );

            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);
            when(userQuotaUsageRepository.findByUserId(eq(TEST_USER_ID))).thenReturn(Optional.of(usage));
            when(userStorageUsageRepository.findByUserId(eq(TEST_USER_ID))).thenReturn(ledgerRows);

            UserStorageLedgerView result = userQuotaQueryService.getStorageLedger(TEST_USER_ID, TEST_TIER_FREE);

            assertThat(result.sources()).hasSize(1);
            assertThat(result.ledgerTotalBytes()).isEqualTo(100000L);
        }

        @Test
        @DisplayName("当ledger中的sourceType或fileType为null时，跳过该row")
        void whenLedgerSourceTypeOrFileTypeIsNull_shouldSkip() {
            SubscriptionQuota quota = createTestQuota(10, 100, 5, 1000000000L);
            UserQuotaUsageEntity usage = createTestQuotaUsage(3, 10, 2, 100000L);
            UserStorageUsageEntity rowWithNullSource = createTestStorageUsage(null, StorageFileType.IMAGE, 5, 50000L);
            UserStorageUsageEntity rowWithNullFileType = createTestStorageUsage(StorageSourceType.MEDIA_LIBRARY, null, 5, 50000L);
            List<UserStorageUsageEntity> ledgerRows = List.of(
                    rowWithNullSource,
                    rowWithNullFileType,
                    createTestStorageUsage(StorageSourceType.MEDIA_LIBRARY, StorageFileType.IMAGE, 10, 100000L)
            );

            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);
            when(userQuotaUsageRepository.findByUserId(eq(TEST_USER_ID))).thenReturn(Optional.of(usage));
            when(userStorageUsageRepository.findByUserId(eq(TEST_USER_ID))).thenReturn(ledgerRows);

            UserStorageLedgerView result = userQuotaQueryService.getStorageLedger(TEST_USER_ID, TEST_TIER_FREE);

            assertThat(result.sources()).hasSize(1);
            assertThat(result.ledgerTotalBytes()).isEqualTo(100000L);
        }

        @Test
        @DisplayName("当quotaUsedBytes与ledgerTotalBytes不一致时，正确计算mismatch")
        void whenQuotaUsedDiffersFromLedger_shouldCalculateMismatch() {
            SubscriptionQuota quota = createTestQuota(10, 100, 5, 1000000000L);
            UserQuotaUsageEntity usage = createTestQuotaUsage(3, 10, 2, 600000L);
            List<UserStorageUsageEntity> ledgerRows = List.of(
                    createTestStorageUsage(StorageSourceType.MEDIA_LIBRARY, StorageFileType.IMAGE, 10, 500000L)
            );

            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);
            when(userQuotaUsageRepository.findByUserId(eq(TEST_USER_ID))).thenReturn(Optional.of(usage));
            when(userStorageUsageRepository.findByUserId(eq(TEST_USER_ID))).thenReturn(ledgerRows);

            UserStorageLedgerView result = userQuotaQueryService.getStorageLedger(TEST_USER_ID, TEST_TIER_FREE);

            assertThat(result.quotaUsedBytes()).isEqualTo(600000L);
            assertThat(result.ledgerTotalBytes()).isEqualTo(500000L);
            assertThat(result.mismatchBytes()).isEqualTo(100000L);
        }

        @Test
        @DisplayName("当usage不存在时，quotaUsedBytes为0")
        void whenUsageNotExists_shouldReturnZeroQuotaUsed() {
            SubscriptionQuota quota = createTestQuota(10, 100, 5, 1000000000L);
            List<UserStorageUsageEntity> ledgerRows = List.of(
                    createTestStorageUsage(StorageSourceType.MEDIA_LIBRARY, StorageFileType.IMAGE, 10, 100000L)
            );

            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);
            when(userQuotaUsageRepository.findByUserId(eq(TEST_USER_ID))).thenReturn(Optional.empty());
            when(userStorageUsageRepository.findByUserId(eq(TEST_USER_ID))).thenReturn(ledgerRows);

            UserStorageLedgerView result = userQuotaQueryService.getStorageLedger(TEST_USER_ID, TEST_TIER_FREE);

            assertThat(result.quotaUsedBytes()).isEqualTo(0L);
            assertThat(result.ledgerTotalBytes()).isEqualTo(100000L);
            assertThat(result.mismatchBytes()).isEqualTo(-100000L);
        }

        @Test
        @DisplayName("当tier为null时，使用FREE作为默认值")
        void whenTierIsNull_shouldUseFreeAsDefault() {
            SubscriptionQuota quota = createTestQuota(10, 100, 5, 1000000000L);

            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);

            UserStorageLedgerView result = userQuotaQueryService.getStorageLedger(TEST_USER_ID, null);

            assertThat(result.tier()).isEqualTo(TEST_TIER_FREE);
            verify(subscriptionQuotaFacade).getQuota(TEST_TIER_FREE);
        }

        @Test
        @DisplayName("当ledger中的fileCount或totalBytes为null时，视为0")
        void whenLedgerCountOrBytesIsNull_shouldTreatAsZero() {
            SubscriptionQuota quota = createTestQuota(10, 100, 5, 1000000000L);
            UserQuotaUsageEntity usage = createTestQuotaUsage(3, 10, 2, 0L);
            UserStorageUsageEntity rowWithNullCount = createTestStorageUsage(StorageSourceType.MEDIA_LIBRARY, StorageFileType.IMAGE, 0, 100000L);
            rowWithNullCount.setFileCount(null);
            UserStorageUsageEntity rowWithNullBytes = createTestStorageUsage(StorageSourceType.MEDIA_LIBRARY, StorageFileType.VIDEO, 5, 0L);
            rowWithNullBytes.setTotalBytes(null);
            List<UserStorageUsageEntity> ledgerRows = List.of(
                    rowWithNullCount,
                    rowWithNullBytes
            );

            when(subscriptionQuotaFacade.getQuota(eq(TEST_TIER_FREE))).thenReturn(quota);
            when(userQuotaUsageRepository.findByUserId(eq(TEST_USER_ID))).thenReturn(Optional.of(usage));
            when(userStorageUsageRepository.findByUserId(eq(TEST_USER_ID))).thenReturn(ledgerRows);

            UserStorageLedgerView result = userQuotaQueryService.getStorageLedger(TEST_USER_ID, TEST_TIER_FREE);

            assertThat(result.ledgerTotalBytes()).isEqualTo(100000L);
            assertThat(result.sources().get(0).totalCount()).isEqualTo(5);
        }
    }
}
