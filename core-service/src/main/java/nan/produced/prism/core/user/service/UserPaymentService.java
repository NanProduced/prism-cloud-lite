package nan.produced.prism.core.user.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.exception.InfraException;
import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.integration.payment.client.PaymentInternalClient;
import nan.produced.prism.core.integration.payment.client.PaymentInternalClient.CancelSubscriptionRequest;
import nan.produced.prism.core.integration.payment.client.PaymentInternalClient.CreateOrderRequest;
import nan.produced.prism.core.integration.payment.client.PaymentInternalClient.SubscriptionActionRequest;
import nan.produced.prism.core.security.api.CloudAuthContext;
import nan.produced.prism.core.user.dto.PaymentCancelSubscriptionRequest;
import nan.produced.prism.core.user.dto.PaymentCreateOrderRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPaymentService {

    private static final String PAYMENT_SUCCESS_CODE = "PAY-0000";

    private final PaymentInternalClient paymentInternalClient;

    public nan.produced.prism.core.user.dto.PaymentCreateOrderResponse createOrder(PaymentCreateOrderRequest request) {
        UUID userId = requireCurrentUserUuid();

        if (request == null || !StringUtils.hasText(request.priceId())) {
            throw new InfraException(ErrorCode.INVALID_REQUEST, "priceId is required");
        }

        ApiResponse<nan.produced.prism.core.integration.payment.dto.PaymentCreateOrderResponse> response = paymentInternalClient.createOrder(
            new CreateOrderRequest(
                userId,
                request.priceId(),
                request.productType(),
                request.customerEmail(),
                request.customerName()
            )
        );

        nan.produced.prism.core.integration.payment.dto.PaymentCreateOrderResponse remote = requirePaymentSuccess(response, "创建订单失败");

        return new nan.produced.prism.core.user.dto.PaymentCreateOrderResponse(
            remote.orderId(),
            remote.orderNo(),
            remote.externalOrderNo(),
            remote.status(),
            remote.checkoutUrl()
        );
    }

    public nan.produced.prism.core.user.dto.PaymentOrderDetailResponse getOrder(UUID orderId) {
        UUID userId = requireCurrentUserUuid();

        ApiResponse<nan.produced.prism.core.integration.payment.dto.PaymentOrderDetailResponse> response = paymentInternalClient.getOrder(orderId);
        nan.produced.prism.core.integration.payment.dto.PaymentOrderDetailResponse remote = requirePaymentSuccess(response, "查询订单失败");

        return new nan.produced.prism.core.user.dto.PaymentOrderDetailResponse(
            remote.id(),
            remote.userId(),
            remote.orderNo(),
            remote.externalOrderNo(),
            remote.amount(),
            remote.currency(),
            remote.status(),
            remote.productType(),
            remote.priceId(),
            remote.checkoutUrl(),
            remote.createdAt(),
            remote.updatedAt()
        );
    }

    public nan.produced.prism.core.user.dto.PaymentOrderDetailResponse cancelOrder(UUID orderId) {
        UUID userId = requireCurrentUserUuid();

        ApiResponse<nan.produced.prism.core.integration.payment.dto.PaymentOrderDetailResponse> response = paymentInternalClient.cancelOrder(orderId);
        nan.produced.prism.core.integration.payment.dto.PaymentOrderDetailResponse remote = requirePaymentSuccess(response, "取消订单失败");

        return new nan.produced.prism.core.user.dto.PaymentOrderDetailResponse(
            remote.id(),
            remote.userId(),
            remote.orderNo(),
            remote.externalOrderNo(),
            remote.amount(),
            remote.currency(),
            remote.status(),
            remote.productType(),
            remote.priceId(),
            remote.checkoutUrl(),
            remote.createdAt(),
            remote.updatedAt()
        );
    }

    public void cancelSubscription(PaymentCancelSubscriptionRequest request) {
        UUID userId = requireCurrentUserUuid();

        boolean effectiveImmediately = request != null && Boolean.TRUE.equals(request.effectiveImmediately());

        ApiResponse<Void> response = paymentInternalClient.cancelSubscription(
            new CancelSubscriptionRequest(userId, effectiveImmediately)
        );

        requirePaymentSuccess(response, "取消订阅失败");
    }

    public void pauseSubscription() {
        UUID userId = requireCurrentUserUuid();

        ApiResponse<Void> response = paymentInternalClient.pauseSubscription(
            new SubscriptionActionRequest(userId)
        );

        requirePaymentSuccess(response, "暂停订阅失败");
    }

    public void resumeSubscription() {
        UUID userId = requireCurrentUserUuid();

        ApiResponse<Void> response = paymentInternalClient.resumeSubscription(
            new SubscriptionActionRequest(userId)
        );

        requirePaymentSuccess(response, "恢复订阅失败");
    }

    private UUID requireCurrentUserUuid() {
        String userUuid = CloudAuthContext.getCurrentUser().userUuid();
        if (!StringUtils.hasText(userUuid)) {
            throw new InfraException(ErrorCode.NO_AUTHENTICATED_USER, "userUuid is missing in CLOUD_AUTH");
        }
        try {
            return UUID.fromString(userUuid);
        } catch (IllegalArgumentException ex) {
            throw new InfraException(ErrorCode.NO_AUTHENTICATED_USER, "Invalid userUuid in CLOUD_AUTH: " + userUuid, ex);
        }
    }

    private <T> T requirePaymentSuccess(ApiResponse<T> response, String message) {
        if (response == null) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, message + ": null response");
        }
        if (!PAYMENT_SUCCESS_CODE.equals(response.getCode())) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, message + ": " + response.getMessage());
        }
        return response.getData();
    }
}
