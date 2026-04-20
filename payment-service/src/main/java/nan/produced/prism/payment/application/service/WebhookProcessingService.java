package nan.produced.prism.payment.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.payment.common.exception.BizException;
import nan.produced.prism.payment.common.exception.ErrorCode;
import nan.produced.prism.payment.domain.model.PaymentSubscriptionEntity;
import nan.produced.prism.payment.domain.model.SubscriptionStatus;
import nan.produced.prism.payment.domain.model.WebhookEventEntity;
import nan.produced.prism.payment.domain.repository.PaymentOrderRepository;
import nan.produced.prism.payment.domain.repository.PaymentSubscriptionRepository;
import nan.produced.prism.payment.domain.repository.WebhookEventRepository;
import nan.produced.prism.payment.infrastructure.paddle.PaddleClient;
import nan.produced.prism.payment.infrastructure.paddle.dto.PaddleDto.Subscription;
import nan.produced.prism.payment.infrastructure.paddle.dto.PaddleDto.SubscriptionEventData;
import nan.produced.prism.payment.infrastructure.paddle.dto.PaddleDto.Transaction;
import nan.produced.prism.payment.infrastructure.paddle.dto.PaddleDto.TransactionEventData;
import nan.produced.prism.payment.interface_.feign.AuthServiceFeignClient;
import nan.produced.prism.payment.interface_.feign.AuthServiceFeignClient.SubscriptionSyncRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookProcessingService {

    private final WebhookEventRepository webhookEventRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentSubscriptionRepository paymentSubscriptionRepository;
    private final PaddleClient paddleClient;
    private final PaymentOrderService paymentOrderService;
    private final SubscriptionSyncService subscriptionSyncService;
    private final AuthServiceFeignClient authServiceFeignClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public void processWebhook(String signatureHeader, String payload) {
        if (!paddleClient.verifyWebhookSignature(signatureHeader, payload)) {
            log.warn("Webhook signature verification failed");
            throw new BizException(ErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode eventNode = root.get("data");

            if (eventNode == null) {
                log.warn("Invalid webhook payload format: missing data object");
                return;
            }

            String eventId = eventNode.path("event_id").asText();
            String eventType = eventNode.path("event_type").asText();

            if (webhookEventRepository.existsByEventId(eventId)) {
                log.info("Webhook event already processed: eventId={}", eventId);
                return;
            }

            WebhookEventEntity event = new WebhookEventEntity();
            event.setEventId(eventId);
            event.setEventType(eventType);
            event.setPayload(eventNode.toString());
            event.setProcessed(false);
            webhookEventRepository.save(event);

            try {
                processEvent(eventType, eventNode.path("data"));
                webhookEventRepository.markProcessed(eventId);
                log.info("Processed webhook event: eventId={}, eventType={}", eventId, eventType);
            } catch (Exception e) {
                log.error("Failed to process webhook event: eventId={}, eventType={}", eventId, eventType, e);
            }

        } catch (JsonProcessingException e) {
            log.error("Failed to parse webhook payload", e);
            throw new BizException(ErrorCode.INVALID_PARAMETER, "Invalid webhook payload", e);
        }
    }

    private void processEvent(String eventType, JsonNode data) throws JsonProcessingException {
        log.debug("Processing webhook event: type={}", eventType);

        switch (eventType) {
            case "subscription.created" -> handleSubscriptionCreated(data);
            case "subscription.activated" -> handleSubscriptionActivated(data);
            case "subscription.canceled" -> handleSubscriptionCanceled(data);
            case "subscription.past_due" -> handleSubscriptionPastDue(data);
            case "subscription.paused" -> handleSubscriptionPaused(data);
            case "subscription.resumed" -> handleSubscriptionResumed(data);
            case "transaction.completed" -> handleTransactionCompleted(data);
            case "transaction.failed" -> handleTransactionFailed(data);
            default -> log.debug("Unhandled webhook event type: {}", eventType);
        }
    }

    private void handleSubscriptionCreated(JsonNode data) throws JsonProcessingException {
        SubscriptionEventData eventData = objectMapper.treeToValue(data, SubscriptionEventData.class);
        UUID userId = extractUserId(eventData.getCustomData());

        if (userId == null) {
            log.warn("Cannot handle subscription.created: missing userId in customData");
            return;
        }

        Subscription paddleSub = fetchSubscriptionFromPaddle(eventData.getId());
        if (paddleSub != null) {
            subscriptionSyncService.syncSubscriptionFromPaddle(paddleSub, userId);
        }
    }

    private void handleSubscriptionActivated(JsonNode data) throws JsonProcessingException {
        SubscriptionEventData eventData = objectMapper.treeToValue(data, SubscriptionEventData.class);
        UUID userId = extractUserId(eventData.getCustomData());

        if (userId == null) {
            log.warn("Cannot handle subscription.activated: missing userId in customData");
            return;
        }

        Subscription paddleSub = fetchSubscriptionFromPaddle(eventData.getId());
        if (paddleSub != null) {
            subscriptionSyncService.syncSubscriptionFromPaddle(paddleSub, userId);
        }
    }

    private void handleSubscriptionCanceled(JsonNode data) throws JsonProcessingException {
        SubscriptionEventData eventData = objectMapper.treeToValue(data, SubscriptionEventData.class);

        paymentSubscriptionRepository.findByExternalSubscriptionId(eventData.getId()).ifPresent(subscription -> {
            subscription.setStatus(SubscriptionStatus.CANCELED);
            if (eventData.getCanceledAt() != null) {
                subscription.setCurrentPeriodEnd(eventData.getCanceledAt());
            }
            paymentSubscriptionRepository.save(subscription);

            syncToAuthService(subscription);
            log.info("Subscription canceled: externalId={}", eventData.getId());
        });
    }

    private void handleSubscriptionPastDue(JsonNode data) throws JsonProcessingException {
        SubscriptionEventData eventData = objectMapper.treeToValue(data, SubscriptionEventData.class);

        paymentSubscriptionRepository.findByExternalSubscriptionId(eventData.getId()).ifPresent(subscription -> {
            subscription.setStatus(SubscriptionStatus.PAST_DUE);
            paymentSubscriptionRepository.save(subscription);

            syncToAuthService(subscription);
            log.info("Subscription past due: externalId={}", eventData.getId());
        });
    }

    private void handleSubscriptionPaused(JsonNode data) throws JsonProcessingException {
        SubscriptionEventData eventData = objectMapper.treeToValue(data, SubscriptionEventData.class);

        paymentSubscriptionRepository.findByExternalSubscriptionId(eventData.getId()).ifPresent(subscription -> {
            subscription.setStatus(SubscriptionStatus.PAUSED);
            paymentSubscriptionRepository.save(subscription);

            syncToAuthService(subscription);
            log.info("Subscription paused: externalId={}", eventData.getId());
        });
    }

    private void handleSubscriptionResumed(JsonNode data) throws JsonProcessingException {
        SubscriptionEventData eventData = objectMapper.treeToValue(data, SubscriptionEventData.class);

        paymentSubscriptionRepository.findByExternalSubscriptionId(eventData.getId()).ifPresent(subscription -> {
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            paymentSubscriptionRepository.save(subscription);

            syncToAuthService(subscription);
            log.info("Subscription resumed: externalId={}", eventData.getId());
        });
    }

    private void handleTransactionCompleted(JsonNode data) throws JsonProcessingException {
        TransactionEventData eventData = objectMapper.treeToValue(data, TransactionEventData.class);

        Transaction transaction = fetchTransactionFromPaddle(eventData.getId());
        if (transaction != null) {
            paymentOrderService.updateOrderStatusFromTransaction(transaction);

            if (transaction.getSubscriptionId() != null) {
                UUID userId = extractUserId(eventData.getCustomData());
                if (userId != null) {
                    Subscription paddleSub = fetchSubscriptionFromPaddle(transaction.getSubscriptionId());
                    if (paddleSub != null) {
                        subscriptionSyncService.syncSubscriptionFromPaddle(paddleSub, userId);
                    }
                }
            }
        }
    }

    private void handleTransactionFailed(JsonNode data) throws JsonProcessingException {
        TransactionEventData eventData = objectMapper.treeToValue(data, TransactionEventData.class);

        Transaction transaction = fetchTransactionFromPaddle(eventData.getId());
        if (transaction != null) {
            paymentOrderService.updateOrderStatusFromTransaction(transaction);
        }
    }

    private UUID extractUserId(Map<String, Object> customData) {
        if (customData == null) {
            return null;
        }
        Object userIdObj = customData.get("userId");
        if (userIdObj == null) {
            return null;
        }
        try {
            return UUID.fromString(userIdObj.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Subscription fetchSubscriptionFromPaddle(String subscriptionId) {
        try {
            return paddleClient.getSubscription(subscriptionId);
        } catch (Exception e) {
            log.error("Failed to fetch subscription from Paddle: id={}", subscriptionId, e);
            return null;
        }
    }

    private Transaction fetchTransactionFromPaddle(String transactionId) {
        try {
            return paddleClient.getTransaction(transactionId);
        } catch (Exception e) {
            log.error("Failed to fetch transaction from Paddle: id={}", transactionId, e);
            return null;
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
}
