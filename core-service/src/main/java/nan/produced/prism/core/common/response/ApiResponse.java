package nan.produced.prism.core.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nan.produced.prism.core.common.exception.ErrorCode;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private String code;
    private String message;
    private T data;
    private ApiResponseMeta meta;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .code(ErrorCode.SUCCESS.getCode())
            .message(ErrorCode.SUCCESS.getMessage())
            .data(data)
            .build();
    }

    public static <T> ApiResponse<T> success() {
        return ApiResponse.<T>builder()
            .code(ErrorCode.SUCCESS.getCode())
            .message(ErrorCode.SUCCESS.getMessage())
            .data(null)
            .build();
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return ApiResponse.<T>builder()
            .code(errorCode.getCode())
            .message(errorCode.getMessage())
            .data(null)
            .build();
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return ApiResponse.<T>builder()
            .code(errorCode.getCode())
            .message(message)
            .data(null)
            .build();
    }

    public ApiResponse<T> withMeta(ApiResponseMeta meta) {
        this.meta = meta;
        return this;
    }

    public ApiResponse<T> withMeta(String traceId, Long durationMs, String from) {
        this.meta = ApiResponseMeta.builder()
            .traceId(traceId)
            .durationMs(durationMs)
            .from(from)
            .build();
        return this;
    }
}
