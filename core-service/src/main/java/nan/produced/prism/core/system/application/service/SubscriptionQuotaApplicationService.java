package nan.produced.prism.core.system.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.util.JsonUtils;
import nan.produced.prism.core.system.api.SubscriptionQuota;
import nan.produced.prism.core.system.api.SubscriptionQuotaFacade;
import nan.produced.prism.core.system.application.domain.PlatformConfigType;
import nan.produced.prism.core.system.application.domain.subscription.SubscriptionQuotaConfig;
import nan.produced.prism.core.system.infrastructure.persistence.PlatformConfigRepositoryJpa;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionQuotaApplicationService implements SubscriptionQuotaFacade {

    private static final String TIER_FREE = "FREE";
    private static final String TIER_PRO = "PRO";

    private static final SubscriptionQuota DEFAULT_FREE = new SubscriptionQuota(
            20,
            2L * 1024 * 1024 * 1024, // 2GB
            20,
            100,
            3
    );

    private static final SubscriptionQuota DEFAULT_PRO = new SubscriptionQuota(
            100,
            50L * 1024 * 1024 * 1024, // 50GB
            200,
            1000,
            20
    );

    private final PlatformConfigRepositoryJpa platformConfigRepositoryJpa;

    @Override
    public SubscriptionQuota getQuota(String tier) {
        String normalizedTier = normalizeTier(tier);
        SubscriptionQuota defaults = TIER_PRO.equals(normalizedTier) ? DEFAULT_PRO : DEFAULT_FREE;

        return platformConfigRepositoryJpa
                .findByConfigTypeAndConfigKeyAndEnabledTrue(PlatformConfigType.SUBSCRIPTION_QUOTA, normalizedTier)
                .map(cfg -> toQuota(cfg.getConfigValue(), defaults))
                .orElse(defaults);
    }

    private SubscriptionQuota toQuota(String configValueJson, SubscriptionQuota defaults) {
        if (configValueJson == null || configValueJson.isBlank()) {
            return defaults;
        }

        try {
            SubscriptionQuotaConfig config = JsonUtils.fromJson(configValueJson, SubscriptionQuotaConfig.class);
            if (config == null) {
                return defaults;
            }

            return new SubscriptionQuota(
                    firstNonNull(config.getDeviceLimit(), defaults.deviceLimit()),
                    firstNonNull(config.getStorageLimitBytes(), defaults.storageLimitBytes()),
                    firstNonNull(config.getProgramLimit(), defaults.programLimit()),
                    firstNonNull(config.getProgramVersionLimit(), defaults.programVersionLimit()),
                    firstNonNull(config.getCustomColumnLimit(), defaults.customColumnLimit())
            );
        } catch (Exception ex) {
            log.warn("Failed to parse subscription quota config, fallback to defaults: {}", ex.getMessage());
            return defaults;
        }
    }

    private static <T> T firstNonNull(T value, T fallback) {
        return value != null ? value : fallback;
    }

    private String normalizeTier(String tier) {
        if (tier == null || tier.isBlank()) {
            return TIER_FREE;
        }
        String normalized = tier.trim().toUpperCase();
        return TIER_PRO.equals(normalized) ? TIER_PRO : TIER_FREE;
    }
}

