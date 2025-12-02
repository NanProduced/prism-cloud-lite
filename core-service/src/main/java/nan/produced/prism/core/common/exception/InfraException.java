package nan.produced.prism.core.common.exception;

public class InfraException extends BaseServiceException {

    public InfraException(ErrorCode errorCode) {
        super(errorCode);
    }

    public InfraException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public InfraException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public InfraException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public InfraException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}
