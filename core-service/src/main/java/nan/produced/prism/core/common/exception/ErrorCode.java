package nan.produced.prism.core.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 统一错误码定义
 * 格式：&lt;领域&gt;-&lt;4位数字&gt;
 *
 * 错误码分类：
 * - CORE-2xxx: 业务核心相关
 * - SYS-5xxx: 平台或未分类异常
 *
 * 符合 service-standards.md 规范：
 * - 用于RPC/Feign调用（ApiResponse）
 * - 用于BFF对前端返回（BffResponse）
 */
@Getter
public enum ErrorCode {

    // ============ 成功 ============
    SUCCESS("CORE-0000", "OK", "操作成功", HttpStatus.OK, false),

    // ============ 业务核心相关 (CORE-2xxx) ============
    USER_ALREADY_EXISTS("CORE-2000", "用户已存在", "该邮箱已被注册，请使用其他邮箱或直接登录", HttpStatus.BAD_REQUEST, false),
    USER_NOT_FOUND_IN_CORE("CORE-2001", "用户在核心服务中不存在", "用户资料不存在", HttpStatus.NOT_FOUND, false),
    USER_PROFILE_CREATION_FAILED("CORE-2002", "用户资料创建失败", "用户资料创建失败，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR, true),

    // ============ 认证相关 (CORE-24xx) ============
    UNAUTHORIZED("CORE-2401", "未授权访问", "请提供有效的认证信息", HttpStatus.UNAUTHORIZED, false),
    INVALID_CLOUD_AUTH_HEADER("CORE-2402", "CLOUD_AUTH头格式错误", "认证信息格式错误，请重新登录", HttpStatus.UNAUTHORIZED, false),
    NO_AUTHENTICATED_USER("CORE-2403", "当前上下文中没有认证用户", "请先完成登录", HttpStatus.UNAUTHORIZED, false),

    // ============ 系统异常 (SYS-5xxx) ============
    INTERNAL_SERVER_ERROR("SYS-5000", "系统内部错误", "服务器内部错误，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR, true),
    EXTERNAL_SERVICE_ERROR("SYS-5001", "外部服务调用失败", "依赖服务暂时不可用，请稍后重试", HttpStatus.BAD_GATEWAY, true);

    private final String code;
    private final String message;                // 技术向信息（开发调试用）
    private final String displayMessage;         // 用户友好的提示
    private final HttpStatus httpStatus;
    private final boolean retryable;             // 是否可重试

    ErrorCode(String code, String message, String displayMessage, HttpStatus httpStatus, boolean retryable) {
        this.code = code;
        this.message = message;
        this.displayMessage = displayMessage;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }
}
