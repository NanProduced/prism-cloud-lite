package nan.produced.prism.payment.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.payment.common.exception.BizException;
import nan.produced.prism.payment.common.exception.ErrorCode;
import nan.produced.prism.payment.domain.model.PaymentEventEntity;
import nan.produced.prism.payment.domain.model.PaymentEventType;
import nan.produced.prism.payment.domain.model.PaymentSubscriptionEntity;
import nan.produced.prism.payment.domain.model.SubscriptionStatus;
import nan.produced.prism.payment.domain.repository.PaymentEventRepository;
import nan.produced.prism.payment.domain.repository.PaymentSubscriptionRepository;
import nan.produced.prism.payment.infrastructure.paddle.PaddleClient;
import nan.produced.prism.payment.infrastructure.paddle.dto.PaddleDto.BillingPeriod;
import nan.produced.prism.payment.infrastructure.paddle.dto.PaddleDto.Subscription;
import nan.produced.prism.payment.interface_.feign.AuthServiceFeignClient;
import nan.produced.prism.payment.interface_.feign.AuthServiceFeignClient.SubscriptionSyncRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionSyncService {

    private final PaymentSubscriptionRepository subscriptionRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final AuthServiceFeignClient authServiceFeignClient;
    private final PaddleClient paddleClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public PaymentSubscriptionEntity syncSubscriptionFromPaddle(Subscription paddleSubscription, UUID userId) {
        String externalId = paddleSubscription.getId();

        Optional<PaymentSubscriptionEntity> existingOpt = subscriptionRepository
            .findByExternalSubscriptionId(externalId);

        PaymentSubscriptionEntity subscription = existingOpt.orElseGet(PaymentSubscriptionEntity::new);

        if (existingOpt.isEmpty()) {
            subscription.setUserId(userId);
            subscription.setExternalSubscriptionId(externalId);
        }

        subscription.setTier("PRO");
        subscription.setStatus(mapPaddleStatus(paddleSubscription.getStatus()));

        BillingPeriod billingPeriod = paddleSubscription.getCurrentBillingPeriod();
        if (billingPeriod != null) {
            subscription.setCurrentPeriodStart(billingPeriod.getStartsAt());
            subscription.setCurrentPeriodEnd(billingPeriod.getEndsAt());
        }

        if (paddleSubscription.getScheduledChange() != null) {
            subscription.setCancelAtPeriodEnd(
                "cancel".equals(paddleSubscription.getScheduledChange().getAction())
            );
        }

        subscription = subscriptionRepository.save(subscription);

        syncToAuthService(subscription);

        recordPaymentEvent(
            userId,
            PaymentEventType.SUBSCRIPTION_SYNCED,
            true,
            null,
            subscription.getId(),
            Map.of("externalId", externalId, "status", subscription.getStatus().name())
        );

        log.info("Synced subscription: externalId={}, userId={}, status={}",
            externalId, userId, subscription.getStatus());

        return subscription;
    }

    @Transactional(readOnly = true)
    public Optional<PaymentSubscriptionEntity> getCurrentSubscription(UUID userId) {
        return subscriptionRepository.findByUserId(userId);
    }

    @Transactional
    public void cancelSubscription(UUID userId, boolean effectiveImmediately) {
        PaymentSubscriptionEntity subscription = subscriptionRepository.findByUserId(userId)
            .orElseThrow(() -> new BizException(ErrorCode.SUBSCRIPTION_NOT_FOUND));

        try {
            Subscription paddleSub = paddleClient.cancelSubscription(
                subscription.getExternalSubscriptionId(),
                effectiveImmediately
            );

            syncSubscriptionFromPaddle(paddleSub, userId);

            log.info("Canceled subscription: userId={}, effectiveImmediately={}",
                userId, effectiveImmediately);

        } catch (Exception e) {
            log.error("Failed to cancel subscription via Paddle API", e);
            throw new BizException(ErrorCode.PADDLE_API_ERROR, "Failed to cancel subscription", e);
        }
    }

    @Transactional
    public void pauseSubscription(UUID userId) {
        PaymentSubscriptionEntity subscription = subscriptionRepository.findByUserId(userId)
            .orElseThrow(() -> new BizException(ErrorCode.SUBSCRIPTION_NOT_FOUND));

        try {
            Subscription paddleSub = paddleClient.pauseSubscription(
                subscription.getExternalSubscriptionId()
            );

            syncSubscriptionFromPaddle(paddleSub, userId);

            log.info("Paused subscription: userId={}", userId);

        } catch (Exception e) {
            log.error("Failed to pause subscription via Paddle API", e);
            throw new BizException(ErrorCode.PADDLE_API_ERROR, "Failed to pause subscription", e);
        }
    }

    @Transactional
    public void resumeSubscription(UUID userId) {
        PaymentSubscriptionEntity subscription = subscriptionRepository.findByUserId(userId)
            .orElseThrow(() -> new BizException(ErrorCode.SUBSCRIPTION_NOT_FOUND));

        try {
            Subscription paddleSub = paddleClient.resumeSubscription(
                subscription.getExternalSubscriptionId()
            );

            syncSubscriptionFromPaddle(paddleSub, userId);

            log.info("Resumed subscription: userId={}", userId);

        } catch (Exception e) {
            log.error("Failed to resume subscription via Paddle API", e);
            throw new BizException(ErrorCode.PADDLE_API_ERROR, "Failed to resume subscription", e);
        }
    }

    private void syncToAuthService(PaymentSubscriptionEntity subscription) {
        try {
            SubscriptionSyncRequest request = new SubscriptionSyncRequest();
            request.setUserId(subscription.getUserId().toString());
            request.setExternalSubscriptionId(subscription.getExternalSubscriptionId());
            request.setTier(subscription.getTier());
            request.setStartAt(subscription.getCurrentPeriodStart());
            request.setEndAt(subscription.getCurrentPeriodEnd());
            request.setStatus(subscription.getStatus().name());

            authServiceFeignClient.syncSubscriptionFromPayment(request);

            log.info("Synced subscription to auth-service: userId={}", subscription.getUserId());

        } catch (Exception e) {
            log.error("Failed to sync subscription to auth-service", e);
        }
    }

    private SubscriptionStatus mapPaddleStatus(String paddleStatus) {
        if (paddleStatus == null) {
            return SubscriptionStatus.ACTIVE;
        }
        return switch (paddleStatus.toLowerCase()) {
            case "active" -> SubscriptionStatus.ACTIVE;
            case "past_due" -> SubscriptionStatus.PAST_DUE;
            case "canceled" -> SubscriptionStatus.CANCELED;
            case "paused" -> SubscriptionStatus.PAUSED;
            case "trialing" -> SubscriptionStatus.TRIALING;
            default -> SubscriptionStatus.ACTIVE;
        };
    }

    private void recordPaymentEvent(UUID userId, PaymentEventType eventType, boolean success,
                                     UUID orderId, UUID subscriptionId, Map<String, Object> metadata) {
        try {
            PaymentEventEntity event = new PaymentEventEntity();
            event.setUserId(userId);
            event.setEventType(eventType);
            event.setSuccess(success);
            event.setOrderId(orderId);
            event.setSubscriptionId(subscriptionId);
            if (metadata != null && !metadata.isEmpty()) {
                event.setMetadata(objectMapper.writeValueAsString(metadata));
            }
            paymentEventRepository.save(event);
        } catch (Exception e) {
            log.warn("Failed to record payment event: {}", e.getMessage());
        }
    }
}
