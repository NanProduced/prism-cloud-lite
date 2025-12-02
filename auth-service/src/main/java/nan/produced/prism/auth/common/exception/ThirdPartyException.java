package nan.produced.prism.auth.common.exception;

/**
 * 第三方异常
 * 用于外部依赖服务返回异常且需要透出的情况
 *
 * @author Nan
 */
public class ThirdPartyException extends BaseServiceException {

    public ThirdPartyException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ThirdPartyException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ThirdPartyException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public ThirdPartyException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public ThirdPartyException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}
