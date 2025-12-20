package nan.produced.prism.auth.internal.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.response.ApiResponse;
import nan.produced.prism.auth.domain.audit.SecurityEventEntity;
import nan.produced.prism.auth.internal.dto.InternalChangePasswordRequest;
import nan.produced.prism.auth.internal.dto.InternalSecurityEventView;
import nan.produced.prism.auth.internal.dto.InternalSecurityHistoryPageView;
import nan.produced.prism.auth.internal.service.InternalAccountSecurityService;
import nan.produced.prism.auth.security.audit.SecurityAuditService;
import nan.produced.prism.auth.security.rememberme.RememberMeTokenService;
import nan.produced.prism.auth.security.rememberme.RememberMeTokenService.RememberedDeviceView;
import nan.produced.prism.auth.utils.TraceUtils;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Internal APIs for account security operations invoked by core-service.
 * <p>
 * All endpoints are under {@code /internal/**} and are protected by the service-signature filter.
 * </p>
 */
@RestController
@RequestMapping("/internal/account/security")
@RequiredArgsConstructor
public class InternalAccountSecurityController {

    private final RememberMeTokenService rememberMeTokenService;
    private final InternalAccountSecurityService internalAccountSecurityService;
    private final SecurityAuditService securityAuditService;

    /**
     * 获取登录设备列表
     * @param userId 用户 ID
     * @param activeSeries 激活的 Series
     * @return
     */
    @GetMapping("/remember-me/tokens")
    public ResponseEntity<ApiResponse<List<RememberedDeviceView>>> listRememberMeTokens(
        @RequestParam("userId") UUID userId,
        @RequestParam(value = "activeSeries", required = false) String activeSeries) {
        List<RememberedDeviceView> devices = rememberMeTokenService.listActiveTokens(userId, activeSeries);
        return ResponseEntity.ok(ApiResponse.success(devices).withMeta(TraceUtils.getTraceId(), null));
    }

    /**
     * 获取安全事件列表
     * @param userId 用户 ID
     * @param page 页码
     * @param size 每页数量
     * @return 安全事件VO
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<InternalSecurityHistoryPageView>> listSecurityHistory(
        @RequestParam("userId") UUID userId,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<SecurityEventEntity> events = securityAuditService.listUserEvents(userId, page, size);
        List<InternalSecurityEventView> items = events.getContent().stream()
            .map(it -> new InternalSecurityEventView(
                it.getId(),
                it.getEventType() == null ? null : it.getEventType().name(),
                it.isSuccess(),
                it.getIpAddress(),
                it.getDeviceName(),
                it.getUserAgent(),
                it.getMetadata(),
                it.getCreatedAt()
            ))
            .toList();
        InternalSecurityHistoryPageView view = new InternalSecurityHistoryPageView(items, events.getNumber(), events.getSize(), events.getTotalElements());
        return ResponseEntity.ok(ApiResponse.success(view).withMeta(TraceUtils.getTraceId(), null));
    }

    @PostMapping("/remember-me/tokens/{series}/revoke")
    public ResponseEntity<ApiResponse<Object>> revokeRememberMeToken(@RequestParam("userId") UUID userId,
                                                                     @PathVariable("series") String series,
                                                                     HttpServletRequest request) {
        rememberMeTokenService.revokeToken(userId, series);
        securityAuditService.recordSessionRevoked(userId, series, request);
        return ResponseEntity.ok(ApiResponse.success().withMeta(TraceUtils.getTraceId(), null));
    }

    @PostMapping("/remember-me/tokens/revoke-all")
    public ResponseEntity<ApiResponse<Object>> revokeAllRememberMeTokens(@RequestParam("userId") UUID userId,
                                                                         HttpServletRequest request) {
        rememberMeTokenService.revokeAll(userId);
        securityAuditService.recordSessionsRevoked(userId, request);
        return ResponseEntity.ok(ApiResponse.success().withMeta(TraceUtils.getTraceId(), null));
    }

    @PostMapping("/password/change")
    public ResponseEntity<ApiResponse<Object>> changePassword(@RequestParam("userId") UUID userId,
                                                              @RequestBody InternalChangePasswordRequest body,
                                                              HttpServletRequest request) {
        try {
            internalAccountSecurityService.changePassword(
                userId,
                body == null ? null : body.currentPassword(),
                body == null ? null : body.newPassword()
            );
            securityAuditService.recordPasswordChanged(userId, request);
            return ResponseEntity.ok(ApiResponse.success().withMeta(TraceUtils.getTraceId(), null));
        } catch (BizException ex) {
            return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(ApiResponse.<Object>error(ex.getErrorCode(), ex.getMessage())
                    .withMeta(TraceUtils.getTraceId(), null));
        }
    }
}
