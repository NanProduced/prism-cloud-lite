package nan.produced.prism.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "用户注册", description = "用户注册相关接口 - 三步注册流程")
@RestController
@RequestMapping("/register")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    /**
     * 申请OTP - 第一步
     * POST /register/request-otp
     *
     * @param request 请求体，包含邮箱地址
     * @return 操作结果
     */
    @Operation(
        summary = "申请注册验证码",
        description = """
            ### 功能说明
            用户注册的第一步：向指定邮箱发送 6 位数字验证码（OTP）

            ### 业务规则
            - 验证码有效期 5 分钟
            - 同一邮箱 1 分钟内只能申请一次
            - 如果邮箱已注册，将返回错误

            ### 后续步骤
            收到验证码后，调用 `/register/verify-otp` 验证
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "验证码发送成功",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = BffResponse.class),
            examples = @ExampleObject(
                name = "成功响应",
                value = """
                    {
                      "success": true,
                      "data": null,
                      "error": null,
                      "traceId": "0af7651916cd43dd8448eb211c80319c"
                    }
                    """
            )
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "请求参数错误",
        content = @Content(
            mediaType = "application/json",
            examples = {
                @ExampleObject(
                    name = "邮箱格式错误",
                    value = """
                        {
                          "success": false,
                          "data": null,
                          "error": {
                            "code": "COMMON-0001",
                            "message": "邮箱格式不正确",
                            "displayMessage": "请输入有效的邮箱地址",
                            "retryable": true
                          },
                          "traceId": "0af7651916cd43dd8448eb211c80319c"
                        }
                        """
                ),
                @ExampleObject(
                    name = "频繁请求",
                    value = """
                        {
                          "success": false,
                          "data": null,
                          "error": {
                            "code": "AUTH-1002",
                            "message": "请求过于频繁",
                            "displayMessage": "请 1 分钟后再试",
                            "retryable": true
                          },
                          "traceId": "0af7651916cd43dd8448eb211c80319c"
                        }
                        """
                )
            }
        )
    )
    @ApiResponse(
        responseCode = "409",
        description = "邮箱已被注册",
        content = @Content(
            mediaType = "application/json",
            examples = @ExampleObject(
                name = "邮箱已存在",
                value = """
                    {
                      "success": false,
                      "data": null,
                      "error": {
                        "code": "AUTH-2001",
                        "message": "邮箱已被注册",
                        "displayMessage": "该邮箱已被使用，请直接登录或使用其他邮箱",
                        "retryable": false
                      },
                      "traceId": "0af7651916cd43dd8448eb211c80319c"
                    }
                    """
            )
        )
    )
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
     * POST /register/verify-otp
     *
     * @param request 请求体，包含邮箱和OTP
     * @return 验证结果（包含临时令牌）
     */
    @Operation(
        summary = "验证注册验证码",
        description = """
            ### 功能说明
            用户注册的第二步：验证邮箱收到的 6 位数字验证码

            ### 业务规则
            - 验证码必须在 5 分钟内使用
            - 同一验证码只能使用一次
            - 验证成功后返回临时令牌（verificationToken）

            ### 后续步骤
            使用返回的 `verificationToken` 调用 `/register/complete` 完成注册
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "验证成功，返回临时令牌",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = BffResponse.class),
            examples = @ExampleObject(
                name = "成功响应",
                value = """
                    {
                      "success": true,
                      "data": {
                        "verificationToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
                      },
                      "error": null,
                      "traceId": "0af7651916cd43dd8448eb211c80319c"
                    }
                    """
            )
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "验证码错误或过期",
        content = @Content(
            mediaType = "application/json",
            examples = {
                @ExampleObject(
                    name = "验证码错误",
                    value = """
                        {
                          "success": false,
                          "data": null,
                          "error": {
                            "code": "AUTH-1003",
                            "message": "验证码错误",
                            "displayMessage": "验证码不正确，请重新获取",
                            "retryable": true
                          },
                          "traceId": "0af7651916cd43dd8448eb211c80319c"
                        }
                        """
                ),
                @ExampleObject(
                    name = "验证码过期",
                    value = """
                        {
                          "success": false,
                          "data": null,
                          "error": {
                            "code": "AUTH-1001",
                            "message": "OTP已过期",
                            "displayMessage": "验证码已过期，请重新获取",
                            "retryable": true
                          },
                          "traceId": "0af7651916cd43dd8448eb211c80319c"
                        }
                        """
                )
            }
        )
    )
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
     * POST /register/complete
     *
     * @param request 请求体，包含邮箱、密码和验证令牌
     * @return 注册结果
     */
    @Operation(
        summary = "完成注册",
        description = """
            ### 功能说明
            用户注册的第三步：提交密码，完成账号创建

            ### 业务规则
            - 必须提供第二步获得的 `verificationToken`
            - 密码要求：至少 8 个字符，包含大小写字母和数字
            - 注册成功后自动创建用户账号
            - 用户资料（昵称等）将在首次登录后初始化

            ### 后续步骤
            注册完成后，前端应引导用户前往登录页面
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "注册成功",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = BffResponse.class),
            examples = @ExampleObject(
                name = "成功响应",
                value = """
                    {
                      "success": true,
                      "data": null,
                      "error": null,
                      "traceId": "0af7651916cd43dd8448eb211c80319c"
                    }
                    """
            )
        )
    )
    @ApiResponse(
        responseCode = "400",
        description = "请求参数错误或令牌无效",
        content = @Content(
            mediaType = "application/json",
            examples = {
                @ExampleObject(
                    name = "验证令牌无效",
                    value = """
                        {
                          "success": false,
                          "data": null,
                          "error": {
                            "code": "AUTH-1004",
                            "message": "验证令牌无效",
                            "displayMessage": "验证已过期，请重新开始注册流程",
                            "retryable": true
                          },
                          "traceId": "0af7651916cd43dd8448eb211c80319c"
                        }
                        """
                ),
                @ExampleObject(
                    name = "密码格式错误",
                    value = """
                        {
                          "success": false,
                          "data": null,
                          "error": {
                            "code": "COMMON-0002",
                            "message": "密码长度不足",
                            "displayMessage": "密码至少需要 8 个字符",
                            "retryable": true
                          },
                          "traceId": "0af7651916cd43dd8448eb211c80319c"
                        }
                        """
                )
            }
        )
    )
    @ApiResponse(
        responseCode = "409",
        description = "邮箱已被注册（并发注册冲突）",
        content = @Content(
            mediaType = "application/json",
            examples = @ExampleObject(
                name = "邮箱已存在",
                value = """
                    {
                      "success": false,
                      "data": null,
                      "error": {
                        "code": "AUTH-2001",
                        "message": "邮箱已被注册",
                        "displayMessage": "该邮箱已被注册，请直接登录",
                        "retryable": false
                      },
                      "traceId": "0af7651916cd43dd8448eb211c80319c"
                    }
                    """
            )
        )
    )
    @PostMapping("/complete")
    public ResponseEntity<BffResponse<Object>> completeRegistration(
        @Valid @RequestBody CompleteRegistrationRequest request
    ) {
        registrationService.completeRegistration(
            request.email(),
            request.password(),
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
    @Schema(description = "申请注册验证码请求")
    public record RequestOtpRequest(
        @Schema(
            description = "用户邮箱地址",
            example = "user@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        String email
    ) {}

    /**
     * 验证OTP请求
     */
    @Schema(description = "验证注册验证码请求")
    public record VerifyOtpRequest(
        @Schema(
            description = "用户邮箱地址（与申请OTP时相同）",
            example = "user@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        String email,

        @Schema(
            description = "邮箱收到的 6 位数字验证码",
            example = "123456",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 6,
            maxLength = 6
        )
        @NotBlank(message = "OTP不能为空")
        String otp
    ) {}

    /**
     * 验证OTP响应
     */
    @Schema(description = "验证验证码成功响应")
    public record VerifyOtpResponse(
        @Schema(
            description = "临时验证令牌，用于完成注册（有效期 10 分钟）",
            example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJlbWFpbCI6InVzZXJAZXhhbXBsZS5jb20iLCJleHAiOjE2ODk1MjQ4MDB9.abcdef123456",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        String verificationToken
    ) {}

    /**
     * 完成注册请求
     */
    @Schema(description = "完成注册请求")
    public record CompleteRegistrationRequest(
        @Schema(
            description = "用户邮箱地址（与申请OTP时相同）",
            example = "user@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        String email,

        @Schema(
            description = "用户密码（至少 8 个字符）",
            example = "MySecure@Pass123",
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 8
        )
        @NotBlank(message = "密码不能为空")
        String password,

        @Schema(
            description = "验证令牌（从第二步验证OTP接口获得）",
            example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJlbWFpbCI6InVzZXJAZXhhbXBsZS5jb20iLCJleHAiOjE2ODk1MjQ4MDB9.abcdef123456",
            requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "验证令牌不能为空")
        String verificationToken
    ) {}

}
