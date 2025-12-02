package nan.produced.prism.core.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 统一错误码定义
 * 格式：&lt;领域&gt;-&lt;4位数字&gt;
 *
 * 错误码分类：
 * - CORE-2xxx: 业务核心相关
 * - SYS-5xxx: 平台或未分类异常
 */
public enum ErrorCode {

    // ============ 成功 ============
    SUCCESS("CORE-0000", "OK", HttpStatus.OK),

    // ============ 业务核心相关 (CORE-2xxx) ============
    USER_ALREADY_EXISTS("CORE-2000", "用户已存在", HttpStatus.BAD_REQUEST),
    USER_NOT_FOUND_IN_CORE("CORE-2001", "用户在核心服务中不存在", HttpStatus.NOT_FOUND),

    // ============ 系统异常 (SYS-5xxx) ============
    INTERNAL_SERVER_ERROR("SYS-5000", "系统内部错误", HttpStatus.INTERNAL_SERVER_ERROR),
    EXTERNAL_SERVICE_ERROR("SYS-5001", "外部服务调用失败", HttpStatus.BAD_GATEWAY);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
