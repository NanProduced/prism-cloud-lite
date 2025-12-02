package nan.produced.prism.auth.common.exception;

/**
 * 基础设施异常
 * 用于 DB、Redis、邮件、第三方 HTTP 等基础设施错误
 * 例如：邮件发送失败、数据库连接失败
 *
 * @author Nan
 */
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
