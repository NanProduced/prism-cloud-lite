package nan.produced.prism.core.notification.email.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.integration.auth.client.AuthSubscriptionInternalClient;
import nan.produced.prism.core.integration.auth.dto.AuthRedeemCodeBatchCreateRequest;
import nan.produced.prism.core.notification.email.AdminMailService;
import nan.produced.prism.core.notification.email.AdminMailTemplate;
import nan.produced.prism.core.notification.email.api.dto.AdminSendInviteEmailRequest;
import nan.produced.prism.core.notification.email.api.dto.AdminSendRedeemEmailRequest;
import nan.produced.prism.core.notification.email.api.dto.AdminSendRedeemEmailResult;
import nan.produced.prism.core.security.api.AdminAuthz;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "邮件（管理端）", description = "管理平台快速发送模板邮件")
@RestController
@RequestMapping("/api/v1/admin/mail")
@RequiredArgsConstructor
public class AdminMailController {

    private static final String AUTH_SUCCESS_CODE = "AUTH-0000";

    private final AdminMailService adminMailService;
    private final AuthSubscriptionInternalClient authSubscriptionInternalClient;

    @Operation(summary = "发送体验邀请邮件（模板）")
    @PostMapping("/invite")
    public ResponseEntity<BffResponse<Object>> sendInvite(@RequestBody AdminSendInviteEmailRequest request) {
        AdminAuthz.requireAdmin();
        if (request == null || !StringUtils.hasText(request.to())) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "to is required");
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put("toEmail", request.to());
        vars.put("name", request.name());
        vars.put("ctaUrl", request.ctaUrl());
        vars.put("noteHtml", request.noteHtml());

        adminMailService.sendTemplateEmail(
                AdminMailTemplate.INVITE_EXPERIENCE,
                request.to(),
                "邀请体验 Prism Cloud Lite",
                "mail/invite-experience",
                vars
        );

        return ResponseEntity.ok(BffResponse.success().withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "生成 Pro 兑换码并发送邮件（模板）")
    @PostMapping("/redeem-code")
    public ResponseEntity<BffResponse<AdminSendRedeemEmailResult>> sendRedeemCode(@RequestBody AdminSendRedeemEmailRequest request) {
        AdminAuthz.requireAdmin();
        if (request == null || !StringUtils.hasText(request.to())) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "to is required");
        }
        if (request.durationDays() == null || request.durationDays() <= 0) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "durationDays must be positive");
        }

        var resp = authSubscriptionInternalClient.createRedeemCodeBatch(
                new AuthRedeemCodeBatchCreateRequest("PRO", request.durationDays(), 1, request.expiresAt())
        );
        if (resp == null || !AUTH_SUCCESS_CODE.equals(resp.getCode()) || resp.getData() == null || resp.getData().isEmpty()) {
            throw new BizException(ErrorCode.EXTERNAL_SERVICE_ERROR, "生成兑换码失败: " + (resp == null ? "null" : resp.getMessage()));
        }
        String code = resp.getData().getFirst();

        Map<String, Object> vars = new HashMap<>();
        vars.put("toEmail", request.to());
        vars.put("name", request.name());
        vars.put("durationDays", request.durationDays());
        vars.put("code", code);
        vars.put("expiresAt", request.expiresAt());

        adminMailService.sendTemplateEmail(
                AdminMailTemplate.PRO_REDEEM_CODE,
                request.to(),
                "你的 Pro 订阅兑换码",
                "mail/pro-redeem-code",
                vars
        );

        return ResponseEntity.ok(BffResponse.success(new AdminSendRedeemEmailResult(code)).withTraceId(TraceUtils.getTraceId()));
    }
}
