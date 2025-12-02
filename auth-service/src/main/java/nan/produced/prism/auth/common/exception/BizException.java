package nan.produced.prism.auth.common.exception;

/**
 * 业务异常
 * 用于业务校验失败、数据不符合预期等场景
 * 例如：注册信息冲突、验证码错误、密码不符合要求
 *
 * @author Nan
 */
public class BizException extends BaseServiceException {

    public BizException(ErrorCode errorCode) {
        super(errorCode);
    }

    public BizException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public BizException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public BizException(ErrorCode errorCode, Object... args) {
        super(errorCode, args);
    }
}
