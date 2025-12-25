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
import nan.produced.prism.auth.security.oauth.google.GoogleLoginService;
import nan.produced.prism.auth.security.password.PasswordResetService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
@Tag(name = "登录接口", description = "BFF 登录接口（密码/邮箱验证码/手机号验证码/Google 快捷登录）")
public class AuthLoginController {

    private final AuthenticationManager authenticationManager;
    private final OAuth2ContinueUrlValidator continueUrlValidator;
    private final CommonLoginOtpService loginOtpService;
    private final PasswordResetService passwordResetService;
    private final GoogleLoginService googleLoginService;
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

    @PostMapping("/request-phone-otp")
    @Operation(
        summary = "请求手机号登录验证码",
        description = "向用户手机发送一次性验证码（阿里云短信身份验证服务 PNV），包含频次控制。"
    )
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    @ApiResponse(responseCode = "400", description = "参数不合法", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    @ApiResponse(responseCode = "404", description = "用户不存在/未绑定手机号", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    @ApiResponse(responseCode = "429", description = "请求过于频繁", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    public ResponseEntity<BffResponse<Object>> requestPhoneOtp(@Valid @RequestBody RequestPhoneOtp request) {
        loginOtpService.requestPhoneOtp(request.getPhone());
        return ResponseEntity.ok(BffResponse.success().withTraceId(TraceUtils.getTraceId()));
    }

    @PostMapping("/request-password-reset")
    @Operation(
        summary = "请求找回密码验证码",
        description = "通过邮箱或手机号发送验证码（邮箱 OTP / 手机短信 PNV），用于找回密码。"
    )
    @ApiResponse(responseCode = "200", description = "请求成功", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    @ApiResponse(responseCode = "400", description = "参数不合法", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    @ApiResponse(responseCode = "404", description = "用户不存在/未绑定手机号", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    @ApiResponse(responseCode = "429", description = "请求过于频繁", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    public ResponseEntity<BffResponse<Object>> requestPasswordReset(@Valid @RequestBody RequestPasswordReset request) {
        boolean hasEmail = StringUtils.hasText(request.getEmail());
        boolean hasPhone = StringUtils.hasText(request.getPhone());
        if (hasEmail == hasPhone) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "either email or phone is required");
        }
        if (hasEmail) {
            passwordResetService.requestResetByEmail(request.getEmail());
        } else {
            passwordResetService.requestResetByPhone(request.getPhone());
        }
        return ResponseEntity.ok(BffResponse.success().withTraceId(TraceUtils.getTraceId()));
    }

    @PostMapping("/confirm-password-reset")
    @Operation(
        summary = "确认找回密码",
        description = "校验验证码并重置密码；成功后会撤销该账户所有 remember-me 设备。"
    )
    @ApiResponse(responseCode = "200", description = "重置成功", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    @ApiResponse(responseCode = "400", description = "参数不合法/验证码错误", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    @ApiResponse(responseCode = "404", description = "用户不存在", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    @ApiResponse(responseCode = "429", description = "验证过于频繁", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    public ResponseEntity<BffResponse<Object>> confirmPasswordReset(@Valid @RequestBody ConfirmPasswordReset request,
                                                                    HttpServletRequest httpRequest) {
        boolean hasEmail = StringUtils.hasText(request.getEmail());
        boolean hasPhone = StringUtils.hasText(request.getPhone());
        if (hasEmail == hasPhone) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "either email or phone is required");
        }
        if (hasEmail) {
            passwordResetService.confirmResetByEmail(request.getEmail(), request.getAuthCode(), request.getNewPassword(), httpRequest);
        } else {
            passwordResetService.confirmResetByPhone(request.getPhone(), request.getAuthCode(), request.getNewPassword(), httpRequest);
        }
        return ResponseEntity.ok(BffResponse.success().withTraceId(TraceUtils.getTraceId()));
    }

    @PostMapping("/google")
    @Operation(
        summary = "Google 快捷登录",
        description = """
            前端通过 Google Identity Services 获取 `id_token` 后调用本接口，后端验证并建立会话。

            - 成功后返回 `redirectUrl`（等同于输入的 continueUrl，经校验后回传）；
            - 后续流程：跳转至 redirectUrl 完成授权码流程（Gateway 回调）。
            """
    )
    @ApiResponse(responseCode = "200", description = "登录成功", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    @ApiResponse(responseCode = "400", description = "参数不合法", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    @ApiResponse(responseCode = "401", description = "Google 凭证无效", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    @ApiResponse(responseCode = "409", description = "Google 账号绑定冲突", content = @Content(schema = @Schema(implementation = BffResponse.class)))
    public ResponseEntity<BffResponse<LoginSuccessPayload>> googleLogin(@Valid @RequestBody GoogleLoginRequest loginRequest,
                                                                        HttpServletRequest request,
                                                                        HttpServletResponse response) {

        String redirectUrl = continueUrlValidator.validate(request, loginRequest.getContinueUrl());
        PrismUserPrincipal principal = googleLoginService.login(loginRequest.getIdToken());

        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        boolean rememberMe = Boolean.TRUE.equals(loginRequest.getRememberMe());
        rememberMeTokenService.handleLoginSuccess(request, response, principal, rememberMe);
        securityAuditService.recordLoginSuccess(request, principal, null, rememberMe);

        LoginSuccessPayload payload = new LoginSuccessPayload(redirectUrl);
        return ResponseEntity.ok(
            BffResponse.success(payload)
                .withTraceId(TraceUtils.getTraceId())
        );
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
                require(StringUtils.hasText(loginRequest.getPhone()), "phone is required");
                require(StringUtils.hasText(loginRequest.getAuthCode()), "authCode is required");
                params.put(LoginAuthTypeConstants.PHONE, loginRequest.getPhone());
                params.put(LoginAuthTypeConstants.AUTH_CODE, loginRequest.getAuthCode());
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

    @Data
    @Schema(name = "RequestPhoneOtp", description = "手机号 OTP 请求体")
    public static class RequestPhoneOtp {

        @NotBlank
        @Schema(description = "接收验证码的手机号", requiredMode = Schema.RequiredMode.REQUIRED, example = "13800138000")
        private String phone;

    }

    @Data
    @Schema(name = "RequestPasswordReset", description = "找回密码验证码请求体（email/phone 二选一）")
    public static class RequestPasswordReset {

        @Schema(description = "邮箱（与 phone 二选一）")
        private String email;

        @Schema(description = "手机号（与 email 二选一）", example = "13800138000")
        private String phone;

    }

    @Data
    @Schema(name = "ConfirmPasswordReset", description = "找回密码确认请求体（email/phone 二选一）")
    public static class ConfirmPasswordReset {

        @Schema(description = "邮箱（与 phone 二选一）")
        private String email;

        @Schema(description = "手机号（与 email 二选一）", example = "13800138000")
        private String phone;

        @NotBlank
        @Schema(description = "验证码（邮箱 OTP / 手机短信验证码）", requiredMode = Schema.RequiredMode.REQUIRED)
        private String authCode;

        @NotBlank
        @Schema(description = "新密码", requiredMode = Schema.RequiredMode.REQUIRED)
        private String newPassword;

    }

    @Data
    @Schema(name = "GoogleLoginRequest", description = "Google 快捷登录请求体")
    public static class GoogleLoginRequest {

        @NotBlank
        @Schema(description = "Google Identity Services 返回的 id_token", requiredMode = Schema.RequiredMode.REQUIRED)
        private String idToken;

        @NotBlank
        @Schema(description = "登录成功后继续访问的 URL", requiredMode = Schema.RequiredMode.REQUIRED)
        private String continueUrl;

        @Schema(description = "是否在当前设备记住登录状态", defaultValue = "false")
        private Boolean rememberMe;

    }

    @Schema(name = "LoginSuccessPayload", description = "登录成功返回数据")
    public record LoginSuccessPayload(
        @Schema(description = "前端需要跳转的 continueUrl")
        String redirectUrl
    ) {}
}
