package nan.produced.prism.core.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 服务异常基类
 * 所有业务异常都应该继承此类
 */
public class BaseServiceException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;
    private final Object[] args;
    private final Object details;

    public BaseServiceException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
        this.args = new Object[0];
        this.details = null;
    }

    public BaseServiceException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
        this.args = new Object[0];
        this.details = null;
    }

    public BaseServiceException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
        this.args = new Object[0];
        this.details = null;
    }

    public BaseServiceException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
        this.args = new Object[0];
        this.details = null;
    }

    public BaseServiceException(ErrorCode errorCode, Object... args) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
        this.args = args;
        this.details = null;
    }

    public BaseServiceException(ErrorCode errorCode, String message, Object details) {
        super(message);
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
        this.args = new Object[0];
        this.details = details;
    }

    public BaseServiceException(ErrorCode errorCode, String message, Object details, Throwable cause) {
        super(message, cause);
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
        this.args = new Object[0];
        this.details = details;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public Object[] getArgs() {
        return args;
    }

    public Object getDetails() {
        return details;
    }
}
