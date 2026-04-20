package nan.produced.prism.payment.interface_.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.payment.application.service.SubscriptionSyncService;
import nan.produced.prism.payment.common.response.BffResponse;
import nan.produced.prism.payment.domain.model.PaymentSubscriptionEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "订阅接口", description = "订阅管理接口")
public class SubscriptionController {

    private final SubscriptionSyncService subscriptionSyncService;

    @GetMapping("/current")
    @Operation(
        summary = "获取当前订阅",
        description = "获取用户当前的订阅状态"
    )
    @ApiResponse(responseCode = "200", description = "查询成功", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    public ResponseEntity<BffResponse<SubscriptionResponse>> getCurrentSubscription(
        @Valid @RequestBody GetSubscriptionRequest request) {

        Optional<PaymentSubscriptionEntity> subscriptionOpt =
            subscriptionSyncService.getCurrentSubscription(request.getUserId());

        if (subscriptionOpt.isEmpty()) {
            return ResponseEntity.ok(BffResponse.success(null));
        }

        PaymentSubscriptionEntity subscription = subscriptionOpt.get();
        SubscriptionResponse response = new SubscriptionResponse(
            subscription.getId().toString(),
            subscription.getUserId().toString(),
            subscription.getExternalSubscriptionId(),
            subscription.getTier(),
            subscription.getStatus().name(),
            subscription.getCurrentPeriodStart(),
            subscription.getCurrentPeriodEnd(),
            subscription.getCancelAtPeriodEnd(),
            subscription.getCreatedAt(),
            subscription.getUpdatedAt()
        );

        return ResponseEntity.ok(BffResponse.success(response));
    }

    @PostMapping("/cancel")
    @Operation(
        summary = "取消订阅",
        description = "取消用户的订阅"
    )
    @ApiResponse(responseCode = "200", description = "取消成功", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    public ResponseEntity<BffResponse<Void>> cancelSubscription(
        @Valid @RequestBody CancelSubscriptionRequest request) {

        subscriptionSyncService.cancelSubscription(
            request.getUserId(),
            Boolean.TRUE.equals(request.getEffectiveImmediately())
        );

        return ResponseEntity.ok(BffResponse.success());
    }

    @PostMapping("/pause")
    @Operation(
        summary = "暂停订阅",
        description = "暂停用户的订阅"
    )
    @ApiResponse(responseCode = "200", description = "暂停成功", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    public ResponseEntity<BffResponse<Void>> pauseSubscription(
        @Valid @RequestBody SubscriptionActionRequest request) {

        subscriptionSyncService.pauseSubscription(request.getUserId());

        return ResponseEntity.ok(BffResponse.success());
    }

    @PostMapping("/resume")
    @Operation(
        summary = "恢复订阅",
        description = "恢复用户已暂停的订阅"
    )
    @ApiResponse(responseCode = "200", description = "恢复成功", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    public ResponseEntity<BffResponse<Void>> resumeSubscription(
        @Valid @RequestBody SubscriptionActionRequest request) {

        subscriptionSyncService.resumeSubscription(request.getUserId());

        return ResponseEntity.ok(BffResponse.success());
    }

    @Data
    @Schema(name = "GetSubscriptionRequest", description = "获取订阅请求")
    public static class GetSubscriptionRequest {

        @NotNull
        @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
        private UUID userId;
    }

    @Data
    @Schema(name = "CancelSubscriptionRequest", description = "取消订阅请求")
    public static class CancelSubscriptionRequest {

        @NotNull
        @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
        private UUID userId;

        @Schema(description = "是否立即生效", defaultValue = "false")
        private Boolean effectiveImmediately;
    }

    @Data
    @Schema(name = "SubscriptionActionRequest", description = "订阅操作请求")
    public static class SubscriptionActionRequest {

        @NotNull
        @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
        private UUID userId;
    }

    @Schema(name = "SubscriptionResponse", description = "订阅响应")
    public record SubscriptionResponse(
        @Schema(description = "订阅ID")
        String id,
        @Schema(description = "用户ID")
        String userId,
        @Schema(description = "Paddle订阅ID")
        String externalSubscriptionId,
        @Schema(description = "订阅等级")
        String tier,
        @Schema(description = "订阅状态")
        String status,
        @Schema(description = "当前周期开始时间")
        Instant currentPeriodStart,
        @Schema(description = "当前周期结束时间")
        Instant currentPeriodEnd,
        @Schema(description = "是否在周期结束时取消")
        Boolean cancelAtPeriodEnd,
        @Schema(description = "创建时间")
        Instant createdAt,
        @Schema(description = "更新时间")
        Instant updatedAt
    ) {}
}
