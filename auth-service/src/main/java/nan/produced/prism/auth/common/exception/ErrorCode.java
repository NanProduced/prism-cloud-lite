package nan.produced.prism.auth.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 统一错误码定义
 * <p>
 * 格式：&lt;领域&gt;-&lt;4位数字&gt;
 * <p>
 * 错误码分类：
 * <ul>
 * <li>AUTH-1xxx: 认证中心相关</li>
 * <li>SYS-5xxx: 平台或未分类异常</li>
 * </ul>
 *
 * @author Nan
 */
@Getter
public enum ErrorCode {

    // ============ 成功 ============
    SUCCESS(
        "AUTH-0000",
        "OK",
        "操作成功",
        HttpStatus.OK,
        false
    ),

    // ============ 认证相关 (AUTH-1xxx) ============
    EMAIL_ALREADY_REGISTERED(
        "AUTH-1001",
        "邮箱已被注册",
        "该邮箱已被注册，请直接登录或使用其他邮箱",
        HttpStatus.BAD_REQUEST,
        false  // 不可重试（业务冲突）
    ),

    INVALID_EMAIL_FORMAT(
        "AUTH-1002",
        "邮箱格式错误",
        "请输入正确的邮箱格式",
        HttpStatus.BAD_REQUEST,
        true  // 可重试（修正后）
    ),

    INVALID_OTP(
        "AUTH-1003",
        "验证码错误或已过期",
        "验证码不正确，请重新获取",
        HttpStatus.BAD_REQUEST,
        true  // 可重试
    ),

    OTP_EXPIRED(
        "AUTH-1004",
        "验证码已过期",
        "验证码已过期，请重新获取",
        HttpStatus.BAD_REQUEST,
        true  // 可重试
    ),

    OTP_REQUEST_TOO_FREQUENT(
        "AUTH-1005",
        "验证码请求过于频繁",
        "请求过于频繁，请稍后再试",
        HttpStatus.TOO_MANY_REQUESTS,
        true  // 可重试（等待后）
    ),

    INVALID_PASSWORD(
        "AUTH-1006",
        "密码不符合要求（至少8位，需要大小写字母和数字）",
        "密码格式不正确，请确保至少8位且包含大小写字母和数字",
        HttpStatus.BAD_REQUEST,
        true  // 可重试（修正后）
    ),

    USER_NOT_FOUND(
        "AUTH-1007",
        "用户不存在",
        "用户不存在",
        HttpStatus.NOT_FOUND,
        false  // 不可重试
    ),

    INVALID_SERVICE_TOKEN(
        "AUTH-1008",
        "无效的服务令牌",
        "服务令牌无效",
        HttpStatus.UNAUTHORIZED,
        false  // 不可重试（权限错误）
    ),

    IP_NOT_WHITELISTED(
        "AUTH-1009",
        "请求IP未被允许",
        "请求IP未被允许",
        HttpStatus.UNAUTHORIZED,
        false  // 不可重试（权限错误）
    ),

    VERIFICATION_TOKEN_EXPIRED(
        "AUTH-1010",
        "验证令牌已过期",
        "验证链接已过期，请重新验证邮箱",
        HttpStatus.BAD_REQUEST,
        true  // 可重试
    ),

    VERIFICATION_TOKEN_INVALID(
        "AUTH-1011",
        "验证令牌无效",
        "验证令牌无效，请重新获取",
        HttpStatus.BAD_REQUEST,
        true  // 可重试
    ),

    VERIFICATION_TOKEN_NOT_FOUND(
        "AUTH-1012",
        "验证令牌不存在或已被使用",
        "验证令牌不存在或已被使用，请重新验证邮箱",
        HttpStatus.BAD_REQUEST,
        true  // 可重试
    ),

    CALCULATE_SIGNATURE_FAILED(
        "AUTH-1013",
        "签名计算失败",
        "签名计算失败",
        HttpStatus.INTERNAL_SERVER_ERROR,
        true  // 可重试（临时错误）
    ),

    INVALID_PARAMETER(
        "AUTH-1014",
        "请求参数不合法",
        "请求参数不合法，请检查输入",
        HttpStatus.BAD_REQUEST,
        true  // 可重试（修正后）
    ),

    OTP_VERIFY_TOO_FREQUENT(
        "AUTH-1015",
        "操作过于频繁",
        "操作过于频繁，请稍后再试",
        HttpStatus.TOO_MANY_REQUESTS,
        true  // 可重试（等待后）
    ),

    INVALID_CREDENTIALS(
        "AUTH-1016",
        "账号或凭证错误",
        "账号或密码/验证码错误，请重试",
        HttpStatus.UNAUTHORIZED,
        true  // 可重试（重新输入）
    ),

    PHONE_NUMBER_VALIDATION_CODE_GAIN_ERROR(
        "AUTH-1017",
        "手机号验证码获取失败",
        "手机号验证码获取失败，请稍后再试",
        HttpStatus.INTERNAL_SERVER_ERROR,
        true  // 可重试（临时错误）
    ),

    PHONE_NUMBER_VALIDATION_CODE_VERIFY_ERROR(
            "AUTH-1018",
            "手机号验证码验证失败",
            "手机号验证码验证失败，请重新获取",
            HttpStatus.BAD_REQUEST,
            true  // 可重试（重新输入）
    ),

    // ============ 系统异常 (SYS-5xxx) ============
    INTERNAL_SERVER_ERROR(
        "SYS-5000",
        "系统内部错误",
        "系统繁忙，请稍后再试",
        HttpStatus.INTERNAL_SERVER_ERROR,
        true  // 可重试
    ),

    EXTERNAL_SERVICE_ERROR(
        "SYS-5001",
        "外部服务调用失败",
        "服务暂时不可用，请稍后再试",
        HttpStatus.BAD_GATEWAY,
        true  // 可重试
    );

    /**
     * 错误码
     */
    private final String code;

    /**
     * 技术向错误信息（用于日志和开发调试）
     */
    private final String message;

    /**
     * 用户友好的错误提示（用于前端显示，支持国际化）
     */
    private final String displayMessage;

    /**
     * HTTP 状态码
     */
    private final HttpStatus httpStatus;

    /**
     * 是否可重试
     * true: 用户可以修正输入或等待后重试
     * false: 业务冲突或权限错误，重试无意义
     */
    private final boolean retryable;

    ErrorCode(String code, String message, String displayMessage, HttpStatus httpStatus, boolean retryable) {
        this.code = code;
        this.message = message;
        this.displayMessage = displayMessage;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

}
