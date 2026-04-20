package nan.produced.prism.payment.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.payment.common.exception.BizException;
import nan.produced.prism.payment.common.exception.ErrorCode;
import nan.produced.prism.payment.domain.model.OrderStatus;
import nan.produced.prism.payment.domain.model.PaymentEventEntity;
import nan.produced.prism.payment.domain.model.PaymentEventType;
import nan.produced.prism.payment.domain.model.PaymentOrderEntity;
import nan.produced.prism.payment.domain.model.ProductType;
import nan.produced.prism.payment.domain.repository.PaymentEventRepository;
import nan.produced.prism.payment.domain.repository.PaymentOrderRepository;
import nan.produced.prism.payment.infrastructure.paddle.PaddleClient;
import nan.produced.prism.payment.infrastructure.paddle.PaddleConfig;
import nan.produced.prism.payment.infrastructure.paddle.dto.PaddleDto.Transaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOrderService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final PaddleClient paddleClient;
    private final PaddleConfig paddleConfig;
    private final ObjectMapper objectMapper;

    @Transactional
    public PaymentOrderEntity createOrder(UUID userId, String priceId, ProductType productType,
                                           String customerEmail, String customerName) {
        String orderNo = generateOrderNo();

        PaymentOrderEntity order = new PaymentOrderEntity();
        order.setUserId(userId);
        order.setOrderNo(orderNo);
        order.setAmount(BigDecimal.ZERO);
        order.setCurrency("USD");
        order.setStatus(OrderStatus.PENDING);
        order.setProductType(productType);
        order.setPriceId(priceId);

        Map<String, Object> customData = new HashMap<>();
        customData.put("userId", userId.toString());
        customData.put("orderNo", orderNo);

        try {
            Transaction transaction = paddleClient.createTransaction(
                priceId,
                customerEmail,
                customerName,
                customData
            );

            order.setExternalOrderNo(transaction.getId());
            if (transaction.getDetails() != null && transaction.getDetails().getTotals() != null) {
                order.setAmount(new BigDecimal(transaction.getDetails().getTotals().getTotal()));
                order.setCurrency(transaction.getDetails().getTotals().getCurrency());
            }

            if (transaction.getDetails() != null &&
                transaction.getDetails().getPaymentAttempt() != null &&
                transaction.getDetails().getPaymentAttempt().getStatus() != null) {
                String status = transaction.getDetails().getPaymentAttempt().getStatus();
                if ("completed".equalsIgnoreCase(status)) {
                    order.setStatus(OrderStatus.PAID);
                }
            }

            order = paymentOrderRepository.save(order);

            recordPaymentEvent(
                userId,
                PaymentEventType.ORDER_CREATED,
                true,
                order.getId(),
                null,
                Map.of("priceId", priceId, "transactionId", transaction.getId())
            );

            log.info("Created payment order: orderNo={}, userId={}, transactionId={}",
                orderNo, userId, transaction.getId());

            return order;

        } catch (Exception e) {
            log.error("Failed to create Paddle transaction", e);
            throw new BizException(ErrorCode.PADDLE_API_ERROR, "Failed to create transaction", e);
        }
    }

    @Transactional(readOnly = true)
    public PaymentOrderEntity getOrder(UUID orderId) {
        return paymentOrderRepository.findById(orderId)
            .orElseThrow(() -> new BizException(ErrorCode.ORDER_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PaymentOrderEntity getOrderByOrderNo(String orderNo) {
        return paymentOrderRepository.findByOrderNo(orderNo)
            .orElseThrow(() -> new BizException(ErrorCode.ORDER_NOT_FOUND));
    }

    @Transactional
    public PaymentOrderEntity cancelOrder(UUID orderId) {
        PaymentOrderEntity order = paymentOrderRepository.findById(orderId)
            .orElseThrow(() -> new BizException(ErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() == OrderStatus.PAID) {
            throw new BizException(ErrorCode.ORDER_ALREADY_PAID);
        }

        if (order.getStatus() == OrderStatus.CANCELED) {
            return order;
        }

        order.setStatus(OrderStatus.CANCELED);
        order = paymentOrderRepository.save(order);

        recordPaymentEvent(
            order.getUserId(),
            PaymentEventType.ORDER_CANCELED,
            true,
            order.getId(),
            null,
            null
        );

        log.info("Canceled payment order: orderId={}", orderId);
        return order;
    }

    @Transactional
    public void updateOrderStatusFromTransaction(Transaction transaction) {
        String transactionId = transaction.getId();

        paymentOrderRepository.findByExternalOrderNo(transactionId).ifPresent(order -> {
            OrderStatus oldStatus = order.getStatus();
            String transactionStatus = transaction.getStatus();

            OrderStatus newStatus = mapTransactionStatus(transactionStatus);

            if (oldStatus != newStatus) {
                order.setStatus(newStatus);
                paymentOrderRepository.save(order);

                PaymentEventType eventType = switch (newStatus) {
                    case PAID -> PaymentEventType.ORDER_PAID;
                    case FAILED -> PaymentEventType.ORDER_FAILED;
                    case CANCELED -> PaymentEventType.ORDER_CANCELED;
                    case REFUNDED -> PaymentEventType.ORDER_REFUNDED;
                    default -> null;
                };

                if (eventType != null) {
                    recordPaymentEvent(
                        order.getUserId(),
                        eventType,
                        true,
                        order.getId(),
                        null,
                        Map.of("oldStatus", oldStatus.name(), "newStatus", newStatus.name())
                    );
                }

                log.info("Updated order status: orderId={}, oldStatus={}, newStatus={}",
                    order.getId(), oldStatus, newStatus);
            }
        });
    }

    private OrderStatus mapTransactionStatus(String transactionStatus) {
        if (transactionStatus == null) {
            return OrderStatus.PENDING;
        }
        return switch (transactionStatus.toLowerCase()) {
            case "completed", "paid" -> OrderStatus.PAID;
            case "failed" -> OrderStatus.FAILED;
            case "canceled" -> OrderStatus.CANCELED;
            case "refunded" -> OrderStatus.REFUNDED;
            default -> OrderStatus.PENDING;
        };
    }

    private String generateOrderNo() {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String random = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "PO" + timestamp + random;
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
