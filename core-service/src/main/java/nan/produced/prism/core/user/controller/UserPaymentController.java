package nan.produced.prism.core.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.user.dto.PaymentCancelSubscriptionRequest;
import nan.produced.prism.core.user.dto.PaymentCreateOrderRequest;
import nan.produced.prism.core.user.dto.PaymentCreateOrderResponse;
import nan.produced.prism.core.user.dto.PaymentOrderDetailResponse;
import nan.produced.prism.core.user.service.UserPaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "支付入口（用户）", description = "Dashboard Settings - Payment & Subscription")
@RestController
@RequestMapping("/api/v1/user/payment")
@RequiredArgsConstructor
public class UserPaymentController {

    private final UserPaymentService userPaymentService;

    @PostMapping("/create-order")
    @Operation(
        summary = "创建支付订单",
        description = "创建支付订单并返回Paddle Checkout信息"
    )
    @ApiResponse(
        responseCode = "200",
        description = "创建成功",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentCreateOrderResponse.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    public ResponseEntity<BffResponse<PaymentCreateOrderResponse>> createOrder(
        @RequestBody PaymentCreateOrderRequest request) {

        PaymentCreateOrderResponse response = userPaymentService.createOrder(request);
        return ResponseEntity.ok(BffResponse.success(response).withTraceId(TraceUtils.getTraceId()));
    }

    @GetMapping("/orders/{orderId}")
    @Operation(
        summary = "查询订单状态",
        description = "根据订单ID查询订单状态"
    )
    @ApiResponse(
        responseCode = "200",
        description = "查询成功",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentOrderDetailResponse.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    public ResponseEntity<BffResponse<PaymentOrderDetailResponse>> getOrder(
        @PathVariable("orderId") UUID orderId) {

        PaymentOrderDetailResponse response = userPaymentService.getOrder(orderId);
        return ResponseEntity.ok(BffResponse.success(response).withTraceId(TraceUtils.getTraceId()));
    }

    @PostMapping("/orders/{orderId}/cancel")
    @Operation(
        summary = "取消订单",
        description = "取消未支付的订单"
    )
    @ApiResponse(
        responseCode = "200",
        description = "取消成功",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaymentOrderDetailResponse.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    public ResponseEntity<BffResponse<PaymentOrderDetailResponse>> cancelOrder(
        @PathVariable("orderId") UUID orderId) {

        PaymentOrderDetailResponse response = userPaymentService.cancelOrder(orderId);
        return ResponseEntity.ok(BffResponse.success(response).withTraceId(TraceUtils.getTraceId()));
    }

    @PostMapping("/subscription/cancel")
    @Operation(
        summary = "取消订阅",
        description = "取消当前用户的订阅"
    )
    @ApiResponse(responseCode = "200", description = "取消成功")
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    public ResponseEntity<BffResponse<Void>> cancelSubscription(
        @RequestBody(required = false) PaymentCancelSubscriptionRequest request) {

        userPaymentService.cancelSubscription(request);
        return ResponseEntity.ok(BffResponse.<Void>success().withTraceId(TraceUtils.getTraceId()));
    }

    @PostMapping("/subscription/pause")
    @Operation(
        summary = "暂停订阅",
        description = "暂停当前用户的订阅"
    )
    @ApiResponse(responseCode = "200", description = "暂停成功")
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    public ResponseEntity<BffResponse<Void>> pauseSubscription() {
        userPaymentService.pauseSubscription();
        return ResponseEntity.ok(BffResponse.<Void>success().withTraceId(TraceUtils.getTraceId()));
    }

    @PostMapping("/subscription/resume")
    @Operation(
        summary = "恢复订阅",
        description = "恢复当前用户已暂停的订阅"
    )
    @ApiResponse(responseCode = "200", description = "恢复成功")
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    public ResponseEntity<BffResponse<Void>> resumeSubscription() {
        userPaymentService.resumeSubscription();
        return ResponseEntity.ok(BffResponse.<Void>success().withTraceId(TraceUtils.getTraceId()));
    }
}
