package nan.produced.prism.auth.internal.controller;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.response.ApiResponse;
import nan.produced.prism.auth.internal.dto.InternalChangePasswordRequest;
import nan.produced.prism.auth.internal.service.InternalAccountSecurityService;
import nan.produced.prism.auth.security.rememberme.RememberMeTokenService;
import nan.produced.prism.auth.security.rememberme.RememberMeTokenService.RememberedDeviceView;
import nan.produced.prism.auth.utils.TraceUtils;
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

    @GetMapping("/remember-me/tokens")
    public ResponseEntity<ApiResponse<List<RememberedDeviceView>>> listRememberMeTokens(
        @RequestParam("userId") UUID userId,
        @RequestParam(value = "activeSeries", required = false) String activeSeries) {
        List<RememberedDeviceView> devices = rememberMeTokenService.listActiveTokens(userId, activeSeries);
        return ResponseEntity.ok(ApiResponse.success(devices).withMeta(TraceUtils.getTraceId(), null));
    }

    @PostMapping("/remember-me/tokens/{series}/revoke")
    public ResponseEntity<ApiResponse<Object>> revokeRememberMeToken(@RequestParam("userId") UUID userId,
                                                                     @PathVariable("series") String series) {
        rememberMeTokenService.revokeToken(userId, series);
        return ResponseEntity.ok(ApiResponse.success().withMeta(TraceUtils.getTraceId(), null));
    }

    @PostMapping("/remember-me/tokens/revoke-all")
    public ResponseEntity<ApiResponse<Object>> revokeAllRememberMeTokens(@RequestParam("userId") UUID userId) {
        rememberMeTokenService.revokeAll(userId);
        return ResponseEntity.ok(ApiResponse.success().withMeta(TraceUtils.getTraceId(), null));
    }

    @PostMapping("/password/change")
    public ResponseEntity<ApiResponse<Object>> changePassword(@RequestParam("userId") UUID userId,
                                                              @RequestBody InternalChangePasswordRequest request) {
        try {
            internalAccountSecurityService.changePassword(
                userId,
                request == null ? null : request.currentPassword(),
                request == null ? null : request.newPassword()
            );
            return ResponseEntity.ok(ApiResponse.success().withMeta(TraceUtils.getTraceId(), null));
        } catch (BizException ex) {
            return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(ApiResponse.<Object>error(ex.getErrorCode(), ex.getMessage())
                    .withMeta(TraceUtils.getTraceId(), null));
        }
    }
}
