package nan.produced.prism.auth.controller.account;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.auth.common.response.BffResponse;
import nan.produced.prism.auth.security.SecurityUtils;
import nan.produced.prism.auth.security.principal.PrismUserPrincipal;
import nan.produced.prism.auth.security.rememberme.RememberMeTokenService;
import nan.produced.prism.auth.security.rememberme.RememberMeTokenService.RememberedDeviceView;
import nan.produced.prism.auth.utils.TraceUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * APIs for managing remember-me devices from the account security page.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/account/security")
@Tag(name = "账号安全", description = "管理记住登录设备等安全能力")
public class AccountSecurityController {

    private final RememberMeTokenService rememberMeTokenService;

    @GetMapping("/remember-me/tokens")
    @Operation(summary = "获取记住登录设备列表",
            description = "用于账号安全页面展示所有持久登录设备。",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    content = @io.swagger.v3.oas.annotations.media.Content(array = @ArraySchema(schema = @Schema(implementation = RememberedDeviceView.class)))
            ))
    public ResponseEntity<BffResponse<List<RememberedDeviceView>>> listRememberMeTokens(HttpServletRequest request) {
        PrismUserPrincipal principal = SecurityUtils.requirePrincipal();
        String currentSeries = rememberMeTokenService.extractSeriesFromCookie(request).orElse(null);
        List<RememberedDeviceView> devices = rememberMeTokenService.listActiveTokens(principal.getId(), currentSeries);
        return ResponseEntity.ok(
                BffResponse.success(devices)
                        .withTraceId(TraceUtils.getTraceId())
        );
    }

    @DeleteMapping("/remember-me/tokens/{series}")
    @Operation(summary = "注销单个记住的设备")
    public ResponseEntity<BffResponse<Object>> revokeRememberMeToken(@PathVariable String series,
                                                                     HttpServletResponse response) {
        PrismUserPrincipal principal = SecurityUtils.requirePrincipal();
        rememberMeTokenService.revokeToken(principal.getId(), series);
        rememberMeTokenService.clearRememberMeCookie(response);
        return ResponseEntity.ok(BffResponse.success().withTraceId(TraceUtils.getTraceId()));
    }

    @DeleteMapping("/remember-me/tokens")
    @Operation(summary = "注销全部记住的设备")
    public ResponseEntity<BffResponse<Object>> revokeAllRememberMeTokens(HttpServletResponse response) {
        PrismUserPrincipal principal = SecurityUtils.requirePrincipal();
        rememberMeTokenService.revokeAll(principal.getId());
        rememberMeTokenService.clearRememberMeCookie(response);
        return ResponseEntity.ok(BffResponse.success().withTraceId(TraceUtils.getTraceId()));
    }
}
