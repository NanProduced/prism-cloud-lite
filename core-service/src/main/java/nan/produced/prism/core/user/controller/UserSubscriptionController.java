package nan.produced.prism.core.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.user.dto.UserSubscriptionHistoryView;
import nan.produced.prism.core.user.dto.UserSubscriptionRedeemRequest;
import nan.produced.prism.core.user.dto.UserSubscriptionView;
import nan.produced.prism.core.user.service.UserSubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "订阅（用户）", description = "Dashboard Settings - Subscription")
@RestController
@RequestMapping("/api/v1/user/subscription")
@RequiredArgsConstructor
public class UserSubscriptionController {

    private final UserSubscriptionService userSubscriptionService;

    @Operation(summary = "获取当前订阅", description = "返回当前用户的订阅等级与到期时间（如有）。")
    @ApiResponse(
        responseCode = "200",
        description = "成功返回订阅信息",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserSubscriptionView.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @GetMapping
    public ResponseEntity<BffResponse<UserSubscriptionView>> getCurrent() {
        UserSubscriptionView view = userSubscriptionService.getCurrentUserSubscription();
        return ResponseEntity.ok(BffResponse.success(view).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "兑换订阅（兑换码）", description = "输入兑换码以模拟订阅，并为当前用户延长 PRO 权益。")
    @ApiResponse(
        responseCode = "200",
        description = "成功兑换并返回最新订阅信息",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserSubscriptionView.class)))
    @ApiResponse(responseCode = "400", description = "兑换码无效或参数不合法")
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @PostMapping("/redeem")
    public ResponseEntity<BffResponse<UserSubscriptionView>> redeem(@RequestBody UserSubscriptionRedeemRequest request) {
        UserSubscriptionView view = userSubscriptionService.redeemCurrentUserSubscription(request);
        return ResponseEntity.ok(BffResponse.success(view).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "查询订阅审计日志", description = "分页返回当前用户的订阅审计事件（如兑换码核销记录）。")
    @ApiResponse(
        responseCode = "200",
        description = "成功返回订阅审计分页",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserSubscriptionHistoryView.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @GetMapping("/history")
    public ResponseEntity<BffResponse<UserSubscriptionHistoryView>> listHistory(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size) {
        UserSubscriptionHistoryView history = userSubscriptionService.listCurrentUserSubscriptionHistory(page, size);
        return ResponseEntity.ok(BffResponse.success(history).withTraceId(TraceUtils.getTraceId()));
    }
}
