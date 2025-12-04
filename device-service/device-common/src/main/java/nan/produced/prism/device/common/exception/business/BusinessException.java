package nan.produced.prism.device.common.exception.business;

import nan.produced.prism.device.common.exception.BaseException;
import nan.produced.prism.device.common.exception.ErrorCode;

public class BusinessException extends BaseException {

    public BusinessException(ErrorCode errorCode) {
        super(errorCode);
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    @Override
    public boolean isBusinessException() {
        return true;
    }

    @Override
    public boolean isTechnicalException() {
        return false;
    }
}
