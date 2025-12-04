package nan.produced.prism.device.common.exception.tech;

import nan.produced.prism.device.common.exception.BaseException;
import nan.produced.prism.device.common.exception.ErrorCode;

/**
 * 技术实现、三方、中间件异常类
 *
 * @author Nan
 */
public class TechException extends BaseException {

    public TechException(ErrorCode errorCode) {
        super(errorCode);
    }

    public TechException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public TechException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public TechException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    @Override
    public boolean isBusinessException() {
        return false;
    }

    @Override
    public boolean isTechnicalException() {
        return true;
    }
}
