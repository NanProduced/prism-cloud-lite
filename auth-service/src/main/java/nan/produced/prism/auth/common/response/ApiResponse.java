package nan.produced.prism.auth.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nan.produced.prism.auth.common.exception.ErrorCode;

/**
 * 统一 API 响应格式
 * <p>
 * RPC/Feign 调用返回格式：
 * {
 *   "code": "AUTH-0000",
 *   "message": "OK",
 *   "data": {...},
 *   "meta": {
 *     "traceId": "xxx",
 *     "durationMs": 12,
 *     "from": "auth-service"
 *   }
 * }
 *
 * @param <T> 数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /**
     * 错误码（0000 表示成功）
     */
    private String code;

    /**
     * 消息（技术向，方便排查）
     */
    private String message;

    /**
     * 业务数据
     */
    private T data;

    /**
     * 响应元数据
     */
    private ApiResponseMeta meta;

    /**
     * 构建成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .code(ErrorCode.SUCCESS.getCode())
            .message(ErrorCode.SUCCESS.getMessage())
            .data(data)
            .build();
    }

    /**
     * 构建成功响应（空数据）
     */
    public static <T> ApiResponse<T> success() {
        return ApiResponse.<T>builder()
            .code(ErrorCode.SUCCESS.getCode())
            .message(ErrorCode.SUCCESS.getMessage())
            .data(null)
            .build();
    }

    /**
     * 构建错误响应
     */
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return ApiResponse.<T>builder()
            .code(errorCode.getCode())
            .message(errorCode.getMessage())
            .data(null)
            .build();
    }

    /**
     * 构建自定义消息的错误响应
     */
    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return ApiResponse.<T>builder()
            .code(errorCode.getCode())
            .message(message)
            .data(null)
            .build();
    }

    /**
     * 构建成功响应并设置元数据
     */
    public ApiResponse<T> withMeta(ApiResponseMeta meta) {
        this.meta = meta;
        return this;
    }

    /**
     * 设置元数据
     */
    public ApiResponse<T> withMeta(String traceId, Long durationMs) {
        this.meta = ApiResponseMeta.builder()
            .traceId(traceId)
            .durationMs(durationMs)
            .from("auth-service")
            .build();
        return this;
    }
}
