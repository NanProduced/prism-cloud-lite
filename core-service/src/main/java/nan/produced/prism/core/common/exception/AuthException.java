package nan.produced.prism.core.common.exception;

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
