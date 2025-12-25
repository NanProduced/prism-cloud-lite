package nan.produced.prism.auth.internal.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.response.ApiResponse;
import nan.produced.prism.auth.domain.subscription.SubscriptionEventEntity;
import nan.produced.prism.auth.domain.subscription.SubscriptionTier;
import nan.produced.prism.auth.internal.dto.InternalRedeemCodeBatchCreateRequest;
import nan.produced.prism.auth.internal.dto.InternalSubscriptionEventView;
import nan.produced.prism.auth.internal.dto.InternalSubscriptionHistoryPageView;
import nan.produced.prism.auth.internal.dto.InternalSubscriptionRedeemRequest;
import nan.produced.prism.auth.internal.dto.InternalSubscriptionView;
import nan.produced.prism.auth.subscription.SubscriptionService;
import nan.produced.prism.auth.utils.TraceUtils;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "内部接口-订阅", description = "仅供服务间调用（core-service/管理平台），前端勿用")
@RestController
@RequestMapping("/internal/subscription")
@RequiredArgsConstructor
public class InternalSubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping
    @Operation(
        summary = "查询用户当前订阅",
        description = """
            供 core-service 获取用户当前订阅等级与期限。

            - 鉴权：service-signature + IP 白名单；
            - 响应体：`ApiResponse<InternalSubscriptionView>`。
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "成功返回订阅信息",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = InternalSubscriptionView.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "签名无效或无权限（service-signature）")
    public ResponseEntity<ApiResponse<InternalSubscriptionView>> getCurrent(
        @Parameter(description = "用户ID（auth-service 内部 UUID）") @RequestParam("userId") UUID userId) {

        SubscriptionService.SubscriptionSnapshot snapshot = subscriptionService.getCurrent(userId);
        InternalSubscriptionView view = new InternalSubscriptionView(
            snapshot.tier().name(),
            snapshot.startAt(),
            snapshot.endAt(),
            snapshot.active()
        );
        return ResponseEntity.ok(ApiResponse.success(view).withMeta(TraceUtils.getTraceId(), null));
    }

    @PostMapping("/redeem")
    @Operation(
        summary = "兑换订阅（兑换码）",
        description = """
            供 core-service 执行兑换码核销并为用户延长订阅权益。

            - 一次兑换码仅可使用一次；
            - 鉴权：service-signature + IP 白名单；
            - 响应体：`ApiResponse<InternalSubscriptionView>`。
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "成功兑换并返回最新订阅信息",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = InternalSubscriptionView.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "兑换码无效或业务校验失败")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "签名无效或无权限（service-signature）")
    public ResponseEntity<ApiResponse<InternalSubscriptionView>> redeem(
        @RequestParam("userId") UUID userId,
        @RequestBody InternalSubscriptionRedeemRequest request) {

        try {
            SubscriptionService.SubscriptionSnapshot snapshot = subscriptionService.redeem(userId, request == null ? null : request.code());
            InternalSubscriptionView view = new InternalSubscriptionView(
                snapshot.tier().name(),
                snapshot.startAt(),
                snapshot.endAt(),
                snapshot.active()
            );
            return ResponseEntity.ok(ApiResponse.success(view).withMeta(TraceUtils.getTraceId(), null));
        } catch (BizException ex) {
            return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(ApiResponse.<InternalSubscriptionView>error(ex.getErrorCode(), ex.getMessage())
                    .withMeta(TraceUtils.getTraceId(), null));
        }
    }

    @GetMapping("/events")
    @Operation(
        summary = "查询订阅审计日志",
        description = """
            分页返回用户订阅相关审计事件（目前主要记录兑换码核销）。

            - `page`：从 0 开始；
            - `size`：每页数量（默认 20，最大 100）；
            - 鉴权：service-signature + IP 白名单；
            - 响应体：`ApiResponse<InternalSubscriptionHistoryPageView>`。
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "成功返回订阅审计分页",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = InternalSubscriptionHistoryPageView.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "签名无效或无权限（service-signature）")
    public ResponseEntity<ApiResponse<InternalSubscriptionHistoryPageView>> listEvents(
        @RequestParam("userId") UUID userId,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size) {

        Page<SubscriptionEventEntity> events = subscriptionService.listEvents(userId, page, size);
        List<InternalSubscriptionEventView> items = events.getContent().stream()
            .map(it -> new InternalSubscriptionEventView(
                it.getId(),
                it.getEventType() == null ? null : it.getEventType().name(),
                it.isSuccess(),
                it.getCode(),
                it.getMetadata(),
                it.getCreatedAt()
            ))
            .toList();

        InternalSubscriptionHistoryPageView view = new InternalSubscriptionHistoryPageView(
            items,
            events.getNumber(),
            events.getSize(),
            events.getTotalElements()
        );
        return ResponseEntity.ok(ApiResponse.success(view).withMeta(TraceUtils.getTraceId(), null));
    }

    @PostMapping("/redeem-codes/batch")
    @Operation(
        summary = "批量生成兑换码（demo）",
        description = """
            供管理平台/运维脚本批量生成兑换码（无需真实支付）。

            - 鉴权：service-signature + IP 白名单；
            - 响应体：`ApiResponse<List<String>>`（codes）。
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "成功生成兑换码",
        content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = String.class))))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数不合法或业务校验失败")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "签名无效或无权限（service-signature）")
    public ResponseEntity<ApiResponse<List<String>>> createBatch(@RequestBody InternalRedeemCodeBatchCreateRequest request) {
        try {
            String tier = request == null ? null : request.tier();
            Integer durationDays = request == null ? null : request.durationDays();
            Integer count = request == null ? null : request.count();

            SubscriptionTier parsedTier = tier == null || tier.isBlank()
                ? SubscriptionTier.PRO
                : SubscriptionTier.fromNullable(tier);
            int parsedDurationDays = durationDays == null ? 0 : durationDays;
            int parsedCount = count == null ? 1 : count;

            List<String> codes = subscriptionService.createRedeemCodes(parsedTier, parsedDurationDays, parsedCount, request == null ? null : request.expiresAt());
            return ResponseEntity.ok(ApiResponse.success(codes).withMeta(TraceUtils.getTraceId(), null));
        } catch (BizException ex) {
            return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(ApiResponse.<List<String>>error(ex.getErrorCode(), ex.getMessage())
                    .withMeta(TraceUtils.getTraceId(), null));
        }
    }
}
