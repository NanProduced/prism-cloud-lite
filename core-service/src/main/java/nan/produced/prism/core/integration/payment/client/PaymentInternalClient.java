package nan.produced.prism.core.integration.payment.client;

import java.util.Map;
import java.util.UUID;
import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.integration.payment.dto.PaymentCreateOrderResponse;
import nan.produced.prism.core.integration.payment.dto.PaymentOrderDetailResponse;
import nan.produced.prism.core.integration.payment.dto.PaymentSubscriptionResponse;
import nan.produced.prism.core.integration.signature.ServiceSignatureFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service", contextId = "payment", configuration = ServiceSignatureFeignConfig.class, path = "/api/v1")
public interface PaymentInternalClient {

    @PostMapping("/payments/create-order")
    ApiResponse<PaymentCreateOrderResponse> createOrder(@RequestBody CreateOrderRequest request);

    @GetMapping("/payments/{orderId}")
    ApiResponse<PaymentOrderDetailResponse> getOrder(@PathVariable("orderId") UUID orderId);

    @PostMapping("/payments/cancel/{orderId}")
    ApiResponse<PaymentOrderDetailResponse> cancelOrder(@PathVariable("orderId") UUID orderId);

    @PostMapping("/subscriptions/current")
    ApiResponse<PaymentSubscriptionResponse> getCurrentSubscription(@RequestBody GetSubscriptionRequest request);

    @PostMapping("/subscriptions/cancel")
    ApiResponse<Void> cancelSubscription(@RequestBody CancelSubscriptionRequest request);

    @PostMapping("/subscriptions/pause")
    ApiResponse<Void> pauseSubscription(@RequestBody SubscriptionActionRequest request);

    @PostMapping("/subscriptions/resume")
    ApiResponse<Void> resumeSubscription(@RequestBody SubscriptionActionRequest request);

    record CreateOrderRequest(
        UUID userId,
        String priceId,
        String productType,
        String customerEmail,
        String customerName
    ) {}

    record GetSubscriptionRequest(
        UUID userId
    ) {}

    record CancelSubscriptionRequest(
        UUID userId,
        Boolean effectiveImmediately
    ) {}

    record SubscriptionActionRequest(
        UUID userId
    ) {}
}
