package nan.produced.prism.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.exception.ErrorCode;
import nan.produced.prism.auth.common.response.BffResponse;
import nan.produced.prism.auth.security.authentication.PrismAuthenticationToken;
import nan.produced.prism.auth.security.login.LoginAuthType;
import nan.produced.prism.auth.security.login.LoginAuthTypeConstants;
import nan.produced.prism.auth.security.login.validator.OAuth2ContinueUrlValidator;
import nan.produced.prism.auth.security.principal.PrismUserPrincipal;
import nan.produced.prism.auth.utils.TraceUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Console admin login endpoint (email + password only).
 *
 * <p>Design constraints:</p>
 * <ul>
 *   <li>No Google/OTP login for console.</li>
 *   <li>No self-registration for console users.</li>
 * </ul>
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/login/admin")
@RequiredArgsConstructor
@Tag(name = "登录接口（Console）", description = "Console 管理员登录接口（仅用户名+密码）")
public class AuthAdminLoginController {

    private final AuthenticationManager authenticationManager;
    private final OAuth2ContinueUrlValidator continueUrlValidator;
    private final HttpSessionSecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @PostMapping
    @Operation(summary = "Console 管理员登录（用户名+密码）")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "登录成功",
            content = @Content(schema = @Schema(implementation = BffResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数不合法",
            content = @Content(schema = @Schema(implementation = BffResponse.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "认证失败",
            content = @Content(schema = @Schema(implementation = BffResponse.class)))
    public ResponseEntity<BffResponse<AuthLoginController.LoginSuccessPayload>> login(@Valid @RequestBody AdminLoginRequest loginRequest,
                                                                                      HttpServletRequest request,
                                                                                      HttpServletResponse response) {
        String redirectUrl = continueUrlValidator.validate(request, loginRequest.getContinueUrl());

        Map<String, String> authParams = new HashMap<>();
        authParams.put(LoginAuthTypeConstants.AUTH_TYPE, LoginAuthType.ADMIN_EMAIL_PWD.name());
        authParams.put(LoginAuthTypeConstants.EMAIL, loginRequest.getUsername());
        authParams.put(LoginAuthTypeConstants.PASSWORD, loginRequest.getPassword());

        try {
            Authentication authentication = authenticationManager.authenticate(new PrismAuthenticationToken(authParams));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);

            PrismUserPrincipal principal = authentication.getPrincipal() instanceof PrismUserPrincipal user ? user : null;
            if (principal == null) {
                throw new BizException(ErrorCode.INVALID_CREDENTIALS);
            }
        } catch (AuthenticationException ex) {
            SecurityContextHolder.clearContext();
            log.warn("Console authentication failed");
            throw new BizException(ErrorCode.INVALID_CREDENTIALS);
        }

        AuthLoginController.LoginSuccessPayload payload = new AuthLoginController.LoginSuccessPayload(redirectUrl);
        return ResponseEntity.ok(BffResponse.success(payload).withTraceId(TraceUtils.getTraceId()));
    }

    @Data
    @Schema(name = "AdminLoginRequest", description = "Console 管理员登录请求体（用户名+密码）")
    public static class AdminLoginRequest {

        @NotBlank
        @Schema(description = "管理员用户名（v1 默认 admin）", requiredMode = Schema.RequiredMode.REQUIRED)
        private String username;

        @NotBlank
        @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED)
        private String password;

        @NotBlank
        @Schema(description = "登录成功后继续访问的 URL（OAuth2 authorize）", requiredMode = Schema.RequiredMode.REQUIRED)
        private String continueUrl;
    }
}
