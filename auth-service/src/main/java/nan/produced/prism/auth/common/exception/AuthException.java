package nan.produced.prism.auth.common.exception;

/**
 * 认证异常
 * 用于令牌无效、权限不足、认证失败等场景
 * 例如：服务令牌无效、IP 不在白名单中
 *
 * @author Nan
 */
public class AuthException extends BaseServiceException {

    public AuthException(ErrorCode errorCode) {
        super(errorCode);
    }

    public AuthException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public AuthException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public AuthException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public AuthException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}
