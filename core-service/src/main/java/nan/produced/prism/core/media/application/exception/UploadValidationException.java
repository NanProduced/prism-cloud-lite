package nan.produced.prism.core.media.application.exception;

import lombok.Getter;
import nan.produced.prism.core.media.application.dto.BetterUploadErrorResponse;

/**
 * Better Upload 上传验证异常
 * <p>
 * 此异常专用于 Better Upload 协议的验证错误。
 * 由 {@link nan.produced.prism.core.media.infrastructure.web.BetterUploadExceptionHandler} 处理，
 * 返回 Better Upload 协议格式的错误响应，而非 BffResponse。
 * <p>
 * 注意：此异常不继承 BaseServiceException，因为 Better Upload 协议有自己的错误格式规范。
 *
 * @author Nan
 */
@Getter
public class UploadValidationException extends RuntimeException {

    private final BetterUploadErrorResponse errorResponse;

    public UploadValidationException(BetterUploadErrorResponse errorResponse) {
        super(errorResponse.getError().getMessage());
        this.errorResponse = errorResponse;
    }

    public UploadValidationException(BetterUploadErrorResponse errorResponse, Throwable cause) {
        super(errorResponse.getError().getMessage(), cause);
        this.errorResponse = errorResponse;
    }
}
