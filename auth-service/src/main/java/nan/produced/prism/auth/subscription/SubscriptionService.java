package nan.produced.prism.auth.subscription;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.exception.ErrorCode;
import nan.produced.prism.auth.domain.subscription.RedeemCodeEntity;
import nan.produced.prism.auth.domain.subscription.SubscriptionEventEntity;
import nan.produced.prism.auth.domain.subscription.SubscriptionEventType;
import nan.produced.prism.auth.domain.subscription.SubscriptionTier;
import nan.produced.prism.auth.domain.subscription.UserSubscriptionEntity;
import nan.produced.prism.auth.domain.subscription.repository.RedeemCodeRepository;
import nan.produced.prism.auth.domain.subscription.repository.SubscriptionEventRepository;
import nan.produced.prism.auth.domain.subscription.repository.UserSubscriptionRepository;
import nan.produced.prism.auth.utils.JsonUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final SubscriptionTier DEFAULT_TIER = SubscriptionTier.FREE;
    private static final int MAX_BATCH_CREATE = 500;

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final RedeemCodeRepository redeemCodeRepository;
    private final SubscriptionEventRepository subscriptionEventRepository;

    @Transactional(readOnly = true)
    public SubscriptionSnapshot getCurrent(UUID userId) {
        if (userId == null) {
            return SubscriptionSnapshot.free();
        }

        Instant now = Instant.now();
        UserSubscriptionEntity subscription = userSubscriptionRepository.findByUserId(userId).orElse(null);
        if (subscription == null || subscription.getTier() == null || subscription.getEndAt() == null) {
            return SubscriptionSnapshot.free();
        }

        SubscriptionTier tier = subscription.getTier();
        Instant endAt = subscription.getEndAt();
        boolean active = SubscriptionTier.PRO.equals(tier) && endAt.isAfter(now);
        if (!active) {
            return SubscriptionSnapshot.free();
        }

        return new SubscriptionSnapshot(tier, subscription.getStartAt(), endAt, true);
    }

    @Transactional(readOnly = true)
    public SubscriptionTier resolveTier(UUID userId) {
        SubscriptionSnapshot snapshot = getCurrent(userId);
        return snapshot.active() ? snapshot.tier() : DEFAULT_TIER;
    }

    @Transactional
    public SubscriptionSnapshot redeem(UUID userId, String rawCode) {
        if (userId == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "userId is required");
        }

        String code = normalizeCode(rawCode);
        Instant now = Instant.now();

        RedeemCodeEntity redeemCode = redeemCodeRepository.findByCode(code)
            .orElseThrow(() -> new BizException(ErrorCode.SUBSCRIPTION_REDEEM_CODE_INVALID));

        if (!Boolean.TRUE.equals(redeemCode.getEnabled())) {
            throw new BizException(ErrorCode.SUBSCRIPTION_REDEEM_CODE_INVALID);
        }
        if (redeemCode.getRedeemedAt() != null) {
            throw new BizException(ErrorCode.SUBSCRIPTION_REDEEM_CODE_INVALID);
        }
        if (redeemCode.getExpiresAt() != null && !redeemCode.getExpiresAt().isAfter(now)) {
            throw new BizException(ErrorCode.SUBSCRIPTION_REDEEM_CODE_INVALID);
        }

        int durationDays = redeemCode.getDurationDays() == null ? 0 : redeemCode.getDurationDays();
        if (durationDays <= 0) {
            throw new BizException(ErrorCode.SUBSCRIPTION_REDEEM_CODE_INVALID);
        }

        int updated = redeemCodeRepository.markRedeemed(redeemCode.getId(), userId, now);
        if (updated == 0) {
            throw new BizException(ErrorCode.SUBSCRIPTION_REDEEM_CODE_INVALID);
        }

        UserSubscriptionEntity subscription = userSubscriptionRepository.findByUserId(userId).orElse(null);
        SubscriptionTier oldTier = subscription == null ? SubscriptionTier.FREE : SubscriptionTier.fromNullable(subscription.getTier() == null ? null : subscription.getTier().name());
        Instant oldEndAt = subscription == null ? null : subscription.getEndAt();

        Instant base = oldEndAt != null && oldEndAt.isAfter(now) ? oldEndAt : now;
        Instant newEndAt = base.plus(Duration.ofDays(durationDays));
        SubscriptionTier newTier = redeemCode.getTier() == null ? SubscriptionTier.PRO : redeemCode.getTier();

        UserSubscriptionEntity target = subscription != null ? subscription : new UserSubscriptionEntity();
        target.setUserId(userId);
        target.setTier(newTier);
        if (target.getStartAt() == null || oldEndAt == null || !oldEndAt.isAfter(now)) {
            target.setStartAt(now);
        }
        target.setEndAt(newEndAt);
        userSubscriptionRepository.save(target);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("oldTier", oldTier.name());
        metadata.put("newTier", newTier.name());
        metadata.put("oldEndAt", oldEndAt);
        metadata.put("newEndAt", newEndAt);
        metadata.put("durationDays", durationDays);
        recordRedeemEvent(userId, true, code, metadata);

        return new SubscriptionSnapshot(newTier, target.getStartAt(), newEndAt, true);
    }

    @Transactional(readOnly = true)
    public Page<SubscriptionEventEntity> listEvents(UUID userId, int page, int size) {
        if (userId == null) {
            return Page.empty();
        }
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        return subscriptionEventRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(safePage, safeSize));
    }

    @Transactional
    public List<String> createRedeemCodes(SubscriptionTier tier, int durationDays, int count, Instant expiresAt) {
        if (tier == null) {
            tier = SubscriptionTier.PRO;
        }
        if (durationDays <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "durationDays must be > 0");
        }

        int safeCount = Math.min(MAX_BATCH_CREATE, Math.max(1, count));
        List<String> codes = new ArrayList<>(safeCount);

        int attempts = 0;
        while (codes.size() < safeCount && attempts < safeCount * 10) {
            attempts++;
            String code = generateCode();
            RedeemCodeEntity entity = new RedeemCodeEntity();
            entity.setCode(code);
            entity.setTier(tier);
            entity.setDurationDays(durationDays);
            entity.setExpiresAt(expiresAt);
            entity.setEnabled(true);

            try {
                redeemCodeRepository.save(entity);
                codes.add(code);
            } catch (DataIntegrityViolationException ex) {
                log.debug("Redeem code collision, retrying: {}", ex.getMessage());
            }
        }

        if (codes.size() != safeCount) {
            throw new BizException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to generate redeem codes, please retry");
        }
        return codes;
    }

    private void recordRedeemEvent(UUID userId, boolean success, String code, Map<String, Object> metadata) {
        try {
            SubscriptionEventEntity event = new SubscriptionEventEntity();
            event.setUserId(userId);
            event.setEventType(SubscriptionEventType.REDEEM_CODE);
            event.setSuccess(success);
            event.setCode(code);
            if (metadata != null && !metadata.isEmpty()) {
                event.setMetadata(JsonUtils.toJson(metadata));
            }
            subscriptionEventRepository.save(event);
        } catch (Exception ex) {
            log.warn("Failed to record subscription event: {}", ex.getMessage());
        }
    }

    private String normalizeCode(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException(ErrorCode.SUBSCRIPTION_REDEEM_CODE_INVALID);
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new BizException(ErrorCode.SUBSCRIPTION_REDEEM_CODE_INVALID);
        }
        return trimmed
            .replace("-", "")
            .replace(" ", "")
            .toUpperCase(Locale.ROOT);
    }

    private String generateCode() {
        String raw = UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        return "PR" + raw.substring(0, 14);
    }

    @Transactional
    public void syncFromPayment(UUID userId, String tier, Instant startAt, Instant endAt, String status) {
        Instant now = Instant.now();

        SubscriptionTier targetTier = parseTier(tier);
        boolean isActive = isSubscriptionActive(status, endAt, now);

        UserSubscriptionEntity subscription = userSubscriptionRepository.findByUserId(userId).orElse(null);

        if (!isActive) {
            if (subscription != null) {
                subscription.setTier(SubscriptionTier.FREE);
                userSubscriptionRepository.save(subscription);

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("oldTier", subscription.getTier().name());
                metadata.put("newTier", SubscriptionTier.FREE.name());
                metadata.put("reason", "payment_sync");
                metadata.put("status", status);
                recordSubscriptionEvent(userId, SubscriptionEventType.SYNC_FROM_PAYMENT, true, metadata);
            }
            return;
        }

        if (subscription == null) {
            subscription = new UserSubscriptionEntity();
            subscription.setUserId(userId);
        }

        SubscriptionTier oldTier = subscription.getTier() == null ? SubscriptionTier.FREE : subscription.getTier();
        Instant oldEndAt = subscription.getEndAt();

        subscription.setTier(targetTier);
        subscription.setStartAt(startAt != null ? startAt : now);
        subscription.setEndAt(endAt != null ? endAt : now.plus(Duration.ofDays(30)));

        userSubscriptionRepository.save(subscription);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("oldTier", oldTier.name());
        metadata.put("newTier", targetTier.name());
        metadata.put("oldEndAt", oldEndAt);
        metadata.put("newEndAt", subscription.getEndAt());
        metadata.put("status", status);
        recordSubscriptionEvent(userId, SubscriptionEventType.SYNC_FROM_PAYMENT, true, metadata);

        log.info("Synced subscription from payment: userId={}, tier={}, endAt={}", userId, targetTier, subscription.getEndAt());
    }

    private SubscriptionTier parseTier(String tier) {
        if (tier == null || tier.isBlank()) {
            return SubscriptionTier.FREE;
        }
        String upperTier = tier.toUpperCase(Locale.ROOT);
        return switch (upperTier) {
            case "PRO" -> SubscriptionTier.PRO;
            case "FREE" -> SubscriptionTier.FREE;
            default -> SubscriptionTier.FREE;
        };
    }

    private boolean isSubscriptionActive(String status, Instant endAt, Instant now) {
        if (status == null) {
            return endAt != null && endAt.isAfter(now);
        }
        String upperStatus = status.toUpperCase(Locale.ROOT);
        return switch (upperStatus) {
            case "ACTIVE", "TRIALING" -> true;
            case "CANCELED", "PAST_DUE", "PAUSED" -> false;
            default -> endAt != null && endAt.isAfter(now);
        };
    }

    private void recordSubscriptionEvent(UUID userId, SubscriptionEventType eventType, boolean success, Map<String, Object> metadata) {
        try {
            SubscriptionEventEntity event = new SubscriptionEventEntity();
            event.setUserId(userId);
            event.setEventType(eventType);
            event.setSuccess(success);
            if (metadata != null && !metadata.isEmpty()) {
                event.setMetadata(JsonUtils.toJson(metadata));
            }
            subscriptionEventRepository.save(event);
        } catch (Exception ex) {
            log.warn("Failed to record subscription event: {}", ex.getMessage());
        }
    }

    public record SubscriptionSnapshot(SubscriptionTier tier, Instant startAt, Instant endAt, boolean active) {
        public static SubscriptionSnapshot free() {
            return new SubscriptionSnapshot(SubscriptionTier.FREE, null, null, false);
        }
    }
}
