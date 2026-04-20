package nan.produced.prism.payment.common.exception;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detailMessage;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detailMessage = null;
    }

    public BizException(ErrorCode errorCode, String detailMessage) {
        super(errorCode.getMessage() + ": " + detailMessage);
        this.errorCode = errorCode;
        this.detailMessage = detailMessage;
    }

    public BizException(ErrorCode errorCode, String detailMessage, Throwable cause) {
        super(errorCode.getMessage() + ": " + detailMessage, cause);
        this.errorCode = errorCode;
        this.detailMessage = detailMessage;
    }
}
