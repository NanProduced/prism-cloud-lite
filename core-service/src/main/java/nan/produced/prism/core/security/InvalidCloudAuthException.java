package nan.produced.prism.core.security;

import nan.produced.prism.core.common.exception.AuthException;
import nan.produced.prism.core.common.exception.ErrorCode;

/**
 * CLOUD_AUTH 认证异常
 * 当 CLOUD_AUTH 头缺失、格式错误或解析失败时抛出
 * <p>
 * 符合微服务统一开发规范：
 * - 继承 AuthException（认证相关异常）
 * - 使用 ErrorCode.INVALID_CLOUD_AUTH_HEADER 错误码
 * - 支持 HttpStatus.UNAUTHORIZED (401)
 */
public class InvalidCloudAuthException extends AuthException {

    public InvalidCloudAuthException(ErrorCode errorCode) {
        super(errorCode);
    }

    public InvalidCloudAuthException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public InvalidCloudAuthException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public InvalidCloudAuthException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
