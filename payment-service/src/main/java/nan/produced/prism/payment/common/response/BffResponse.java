package nan.produced.prism.payment.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import nan.produced.prism.payment.common.exception.ErrorCode;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BffResponse<T> {

    private boolean success;
    private T data;
    private ErrorDetail error;
    private String traceId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDetail {
        private String code;
        private String message;
        private String displayMessage;
        private boolean retryable;
    }

    public static <T> BffResponse<T> success() {
        return success(null);
    }

    public static <T> BffResponse<T> success(T data) {
        BffResponse<T> response = new BffResponse<>();
        response.setSuccess(true);
        response.setData(data);
        return response;
    }

    public static <T> BffResponse<T> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.getDisplayMessage());
    }

    public static <T> BffResponse<T> error(ErrorCode errorCode, String displayMessage) {
        BffResponse<T> response = new BffResponse<>();
        response.setSuccess(false);
        response.setError(new ErrorDetail(
            errorCode.getCode(),
            errorCode.getMessage(),
            displayMessage,
            errorCode.isRetryable()
        ));
        return response;
    }

    public BffResponse<T> withTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }
}
