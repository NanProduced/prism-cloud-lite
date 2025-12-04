package nan.produced.prism.device.common.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class BaseException extends RuntimeException{

    /**
     * 错误码
     */
    private final String errorCode;

    /**
     * 错误级别
     */
    private final ErrorLevel level;

    /**
     * HTTP状态码
     */
    private final HttpStatusCode httpStatus;

    /**
     * 异常发生时间
     */
    private final Instant timestamp;

    /**
     * 上下文信息 - 额外的补充信息
     */
    private final transient Map<String, Object> context;

    /**
     * 判断是否为业务异常
     */
    public abstract boolean isBusinessException();

    /**
     * 判断是否为技术异常
     */
    public abstract boolean isTechnicalException();

    protected BaseException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode.getCode();
        this.level = errorCode.getLevel();
        this.httpStatus = errorCode.getHttpStatus();
        this.timestamp = Instant.now();
        this.context = new HashMap<>();
    }

    protected BaseException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode.getCode();
        this.level = errorCode.getLevel();
        this.httpStatus = errorCode.getHttpStatus();
        this.timestamp = Instant.now();
        this.context = new HashMap<>();
    }

    protected BaseException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode.getCode();
        this.level = errorCode.getLevel();
        this.httpStatus = errorCode.getHttpStatus();
        this.timestamp = Instant.now();
        this.context = new HashMap<>();
    }

    protected BaseException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode.getCode();
        this.level = errorCode.getLevel();
        this.httpStatus = errorCode.getHttpStatus();
        this.timestamp = Instant.now();
        this.context = new HashMap<>();
    }
    
}
