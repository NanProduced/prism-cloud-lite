package nan.produced.prism.auth.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 服务异常基类
 * 所有业务异常都应该继承此类
 * <p>
 * 包含错误码、消息、HTTP 状态码和参数
 *
 * @author Nan
 */
@Getter
public class BaseServiceException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String code;
    private final HttpStatus httpStatus;
    private final Object[] args;

    /**
     * 使用错误码创建异常
     *
     * @param errorCode 错误码枚举
     */
    public BaseServiceException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
        this.args = new Object[0];
    }

    /**
     * 使用错误码和自定义消息创建异常
     *
     * @param errorCode 错误码枚举
     * @param message 自定义消息（会替换默认消息）
     */
    public BaseServiceException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
        this.args = new Object[0];
    }

    /**
     * 使用错误码和原因创建异常
     *
     * @param errorCode 错误码枚举
     * @param cause 原始异常
     */
    public BaseServiceException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
        this.args = new Object[0];
    }

    /**
     * 使用错误码、自定义消息和原因创建异常
     *
     * @param errorCode 错误码枚举
     * @param message 自定义消息
     * @param cause 原始异常
     */
    public BaseServiceException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
        this.args = new Object[0];
    }

    /**
     * 使用错误码和参数创建异常
     *
     * @param errorCode 错误码枚举
     * @param args 参数数组（用于消息模板替换）
     */
    public BaseServiceException(ErrorCode errorCode, Object... args) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.code = errorCode.getCode();
        this.httpStatus = errorCode.getHttpStatus();
        this.args = args;
    }

}
