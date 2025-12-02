package nan.produced.prism.auth.controller;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.auth.common.response.BffResponse;
import nan.produced.prism.auth.security.registration.RegistrationService;
import nan.produced.prism.auth.utils.TraceUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 注册控制器
 * 提供邮箱+验证码注册的REST API端点
 * <p>
 * 响应格式遵循 service-standards.md 第3.2节（BFF/前端格式）
 *
 * @author Nan
 */
@RestController
@RequestMapping("/auth/register")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    /**
     * 申请OTP - 第一步
     * POST /auth/register/request-otp
     *
     * @param request 请求体，包含邮箱地址
     * @return 操作结果
     */
    @PostMapping("/request-otp")
    public ResponseEntity<BffResponse<Object>> requestOtp(
        @Valid @RequestBody RequestOtpRequest request
    ) {
        registrationService.requestOtp(request.email());

        return ResponseEntity.ok(
            BffResponse.success()
                .withTraceId(TraceUtils.getTraceId())
        );
    }

    /**
     * 验证OTP - 第二步
     * POST /auth/register/verify-otp
     *
     * @param request 请求体，包含邮箱和OTP
     * @return 验证结果（包含临时令牌）
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<BffResponse<VerifyOtpResponse>> verifyOtp(
        @Valid @RequestBody VerifyOtpRequest request
    ) {
        String verificationToken = registrationService.verifyOtp(request.email(), request.otp());

        return ResponseEntity.ok(
            BffResponse.success(new VerifyOtpResponse(verificationToken))
                .withTraceId(TraceUtils.getTraceId())
        );
    }

    /**
     * 完成注册 - 第三步
     * POST /auth/register/complete
     *
     * @param request 请求体，包含邮箱、密码、显示名称和验证令牌
     * @return 注册结果
     */
    @PostMapping("/complete")
    public ResponseEntity<BffResponse<Object>> completeRegistration(
        @Valid @RequestBody CompleteRegistrationRequest request
    ) {
        registrationService.completeRegistration(
            request.email(),
            request.password(),
            request.displayName(),
            request.verificationToken()
        );

        return ResponseEntity.ok(
            BffResponse.success()
                .withTraceId(TraceUtils.getTraceId())
        );
    }

    // ==================== DTO ====================

    /**
     * 申请OTP请求
     */
    public record RequestOtpRequest(
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        String email
    ) {}

    /**
     * 验证OTP请求
     */
    public record VerifyOtpRequest(
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        String email,

        @NotBlank(message = "OTP不能为空")
        String otp
    ) {}

    /**
     * 验证OTP响应
     */
    public record VerifyOtpResponse(
        String verificationToken
    ) {}

    /**
     * 完成注册请求
     */
    public record CompleteRegistrationRequest(
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        String email,

        @NotBlank(message = "密码不能为空")
        String password,

        String displayName,

        @NotBlank(message = "验证令牌不能为空")
        String verificationToken
    ) {}

}
