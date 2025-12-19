package nan.produced.prism.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
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
import nan.produced.prism.auth.security.login.otp.CommonLoginOtpService;
import nan.produced.prism.auth.security.principal.PrismUserPrincipal;
import nan.produced.prism.auth.security.audit.SecurityAuditService;
import nan.produced.prism.auth.security.rememberme.RememberMeTokenService;
import nan.produced.prism.auth.security.login.validator.OAuth2ContinueUrlValidator;
import nan.produced.prism.auth.utils.TraceUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Validated
@RestController
@RequestMapping("/login")
@RequiredArgsConstructor
@Tag(name = "登录接口", description = "BFF 登录与邮箱 OTP 接口")
public class AuthLoginController {

    private final AuthenticationManager authenticationManager;
    private final OAuth2ContinueUrlValidator continueUrlValidator;
    private final CommonLoginOtpService loginOtpService;
    private final RememberMeTokenService rememberMeTokenService;
    private final SecurityAuditService securityAuditService;
    private final HttpSessionSecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    @PostMapping("/request-email-otp")
    @Operation(
        summary = "请求邮箱登录验证码",
        description = "向用户邮箱发送一次性验证码，包含频次控制。"
    )
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    @ApiResponse(responseCode = "400", description = "参数不合法", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    @ApiResponse(responseCode = "429", description = "请求过于频繁", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    public ResponseEntity<BffResponse<Object>> requestEmailOtp(@Valid @RequestBody RequestEmailOtp request) {
        loginOtpService.requestEmailOtp(request.getEmail());
        return ResponseEntity.ok(BffResponse.success().withTraceId(TraceUtils.getTraceId()));
    }

    @PostMapping
    @Operation(
        summary = "登录",
        description = "支持邮箱+密码、邮箱+验证码及手机号+密码等登录方式，成功后返回可继续跳转的 URL。"
    )
    @ApiResponse(responseCode = "200", description = "登录成功", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    @ApiResponse(responseCode = "400", description = "参数不合法", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    @ApiResponse(responseCode = "401", description = "认证失败", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    public ResponseEntity<BffResponse<LoginSuccessPayload>> login(@Valid @RequestBody LoginRequest loginRequest,
                                                                  HttpServletRequest request,
                                                                  HttpServletResponse response) {

        LoginAuthType authType = resolveAuthType(loginRequest.getAuthType());
        Map<String, String> authParams = buildAuthParams(loginRequest, authType);
        String redirectUrl = continueUrlValidator.validate(request, loginRequest.getContinueUrl());

        try {
            Authentication authentication = authenticationManager.authenticate(new PrismAuthenticationToken(authParams));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);

            boolean rememberMe = Boolean.TRUE.equals(loginRequest.getRememberMe());
            PrismUserPrincipal principal = authentication.getPrincipal() instanceof PrismUserPrincipal user ? user : null;
            rememberMeTokenService.handleLoginSuccess(request, response, principal, rememberMe);
            securityAuditService.recordLoginSuccess(request, principal, authType, rememberMe);
        }
        catch (AuthenticationException ex) {
            SecurityContextHolder.clearContext();
            log.warn("Authentication failed for authType {}", loginRequest.getAuthType());
            throw new BizException(ErrorCode.INVALID_CREDENTIALS);
        }

        LoginSuccessPayload payload = new LoginSuccessPayload(redirectUrl);
        return ResponseEntity.ok(
            BffResponse.success(payload)
                .withTraceId(TraceUtils.getTraceId())
        );
    }

    private LoginAuthType resolveAuthType(String rawAuthType) {
        LoginAuthType authType = LoginAuthType.fromString(rawAuthType);
        if (authType == null) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "authType is invalid");
        }
        return authType;
    }

    private Map<String, String> buildAuthParams(LoginRequest loginRequest, LoginAuthType authType) {
        Map<String, String> params = new HashMap<>();
        params.put(LoginAuthTypeConstants.AUTH_TYPE, authType.name());
        switch (authType) {
            case EMAIL_PWD -> {
                require(StringUtils.hasText(loginRequest.getEmail()), "email is required");
                require(StringUtils.hasText(loginRequest.getPassword()), "password is required");
                params.put(LoginAuthTypeConstants.EMAIL, loginRequest.getEmail());
                params.put(LoginAuthTypeConstants.PASSWORD, loginRequest.getPassword());
            }
            case PHONE_PWD -> {
                require(StringUtils.hasText(loginRequest.getPhone()), "phone is required");
                require(StringUtils.hasText(loginRequest.getPassword()), "password is required");
                params.put(LoginAuthTypeConstants.PHONE, loginRequest.getPhone());
                params.put(LoginAuthTypeConstants.PASSWORD, loginRequest.getPassword());
            }
            case EMAIL_OTP -> {
                require(StringUtils.hasText(loginRequest.getEmail()), "email is required");
                require(StringUtils.hasText(loginRequest.getAuthCode()), "authCode is required");
                params.put(LoginAuthTypeConstants.EMAIL, loginRequest.getEmail());
                params.put(LoginAuthTypeConstants.AUTH_CODE, loginRequest.getAuthCode());
            }
            case PHONE_OTP -> {
                throw new BizException(ErrorCode.INVALID_PARAMETER, "手机号验证码登录暂未开放");
            }
            default -> throw new BizException(ErrorCode.INVALID_PARAMETER, "Unsupported authType");
        }
        return params;
    }

    private void require(boolean expression, String message) {
        if (!expression) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, message);
        }
    }

    @Data
    @Schema(name = "LoginRequest", description = "登录请求体")
    public static class LoginRequest {

        @NotBlank
        @Schema(description = "认证方式：EMAIL_PWD/EMAIL_OTP/PHONE_PWD 等", requiredMode = Schema.RequiredMode.REQUIRED)
        private String authType;

        @Schema(description = "邮箱地址，邮箱登录方式必填")
        private String email;

        @Schema(description = "手机号，PHONE_* 登录方式必填")
        private String phone;

        @Schema(description = "密码，密码登录场景必填")
        private String password;

        @Schema(description = "验证码，OTP 登录场景必填")
        private String authCode;

        @NotBlank
        @Schema(description = "登录成功后继续访问的 URL", requiredMode = Schema.RequiredMode.REQUIRED)
        private String continueUrl;

        @Schema(description = "是否在当前设备记住登录状态", defaultValue = "false")
        private Boolean rememberMe;

    }

    @Data
    @Schema(name = "RequestEmailOtp", description = "邮箱 OTP 请求体")
    public static class RequestEmailOtp {

        @NotBlank
        @Email
        @Schema(description = "接收验证码的邮箱", requiredMode = Schema.RequiredMode.REQUIRED)
        private String email;

    }

    @Schema(name = "LoginSuccessPayload", description = "登录成功返回数据")
    public record LoginSuccessPayload(
        @Schema(description = "前端需要跳转的 continueUrl")
        String redirectUrl
    ) {}
}
