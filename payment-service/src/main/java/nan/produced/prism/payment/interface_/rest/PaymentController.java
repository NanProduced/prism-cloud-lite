package nan.produced.prism.payment.interface_.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.payment.application.service.PaymentOrderService;
import nan.produced.prism.payment.common.response.BffResponse;
import nan.produced.prism.payment.domain.model.OrderStatus;
import nan.produced.prism.payment.domain.model.PaymentOrderEntity;
import nan.produced.prism.payment.domain.model.ProductType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "支付接口", description = "支付订单管理接口")
public class PaymentController {

    private final PaymentOrderService paymentOrderService;

    @PostMapping("/create-order")
    @Operation(
        summary = "创建支付订单",
        description = "创建支付订单，返回Paddle Checkout信息"
    )
    @ApiResponse(responseCode = "200", description = "创建成功", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    public ResponseEntity<BffResponse<CreateOrderResponse>> createOrder(
        @Valid @RequestBody CreateOrderRequest request) {

        UUID userId = request.getUserId();
        String priceId = request.getPriceId();
        ProductType productType = request.getProductType() != null ?
            ProductType.valueOf(request.getProductType().toUpperCase()) : ProductType.SUBSCRIPTION;

        PaymentOrderEntity order = paymentOrderService.createOrder(
            userId,
            priceId,
            productType,
            request.getCustomerEmail(),
            request.getCustomerName()
        );

        CreateOrderResponse response = new CreateOrderResponse(
            order.getId().toString(),
            order.getOrderNo(),
            order.getExternalOrderNo(),
            order.getStatus().name(),
            order.getCheckoutUrl()
        );

        return ResponseEntity.ok(BffResponse.success(response));
    }

    @GetMapping("/{orderId}")
    @Operation(
        summary = "查询订单状态",
        description = "根据订单ID查询订单状态"
    )
    @ApiResponse(responseCode = "200", description = "查询成功", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    public ResponseEntity<BffResponse<OrderDetailResponse>> getOrder(@PathVariable UUID orderId) {
        PaymentOrderEntity order = paymentOrderService.getOrder(orderId);

        OrderDetailResponse response = new OrderDetailResponse(
            order.getId().toString(),
            order.getUserId().toString(),
            order.getOrderNo(),
            order.getExternalOrderNo(),
            order.getAmount(),
            order.getCurrency(),
            order.getStatus().name(),
            order.getProductType().name(),
            order.getPriceId(),
            order.getCheckoutUrl(),
            order.getCreatedAt(),
            order.getUpdatedAt()
        );

        return ResponseEntity.ok(BffResponse.success(response));
    }

    @PostMapping("/cancel/{orderId}")
    @Operation(
        summary = "取消订单",
        description = "取消未支付的订单"
    )
    @ApiResponse(responseCode = "200", description = "取消成功", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    public ResponseEntity<BffResponse<OrderDetailResponse>> cancelOrder(@PathVariable UUID orderId) {
        PaymentOrderEntity order = paymentOrderService.cancelOrder(orderId);

        OrderDetailResponse response = new OrderDetailResponse(
            order.getId().toString(),
            order.getUserId().toString(),
            order.getOrderNo(),
            order.getExternalOrderNo(),
            order.getAmount(),
            order.getCurrency(),
            order.getStatus().name(),
            order.getProductType().name(),
            order.getPriceId(),
            order.getCheckoutUrl(),
            order.getCreatedAt(),
            order.getUpdatedAt()
        );

        return ResponseEntity.ok(BffResponse.success(response));
    }

    @Data
    @Schema(name = "CreateOrderRequest", description = "创建订单请求")
    public static class CreateOrderRequest {

        @NotNull
        @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
        private UUID userId;

        @NotBlank
        @Schema(description = "Paddle Price ID", requiredMode = Schema.RequiredMode.REQUIRED)
        private String priceId;

        @Schema(description = "产品类型：SUBSCRIPTION/ONE_TIME", defaultValue = "SUBSCRIPTION")
        private String productType;

        @Schema(description = "用户邮箱")
        private String customerEmail;

        @Schema(description = "用户姓名")
        private String customerName;
    }

    @Schema(name = "CreateOrderResponse", description = "创建订单响应")
    public record CreateOrderResponse(
        @Schema(description = "订单ID")
        String orderId,
        @Schema(description = "本地订单号")
        String orderNo,
        @Schema(description = "Paddle交易ID")
        String externalOrderNo,
        @Schema(description = "订单状态")
        String status,
        @Schema(description = "Checkout URL")
        String checkoutUrl
    ) {}

    @Schema(name = "OrderDetailResponse", description = "订单详情响应")
    public record OrderDetailResponse(
        @Schema(description = "订单ID")
        String id,
        @Schema(description = "用户ID")
        String userId,
        @Schema(description = "本地订单号")
        String orderNo,
        @Schema(description = "Paddle交易ID")
        String externalOrderNo,
        @Schema(description = "订单金额")
        BigDecimal amount,
        @Schema(description = "货币类型")
        String currency,
        @Schema(description = "订单状态")
        String status,
        @Schema(description = "产品类型")
        String productType,
        @Schema(description = "Paddle Price ID")
        String priceId,
        @Schema(description = "Checkout URL")
        String checkoutUrl,
        @Schema(description = "创建时间")
        Instant createdAt,
        @Schema(description = "更新时间")
        Instant updatedAt
    ) {}
}
