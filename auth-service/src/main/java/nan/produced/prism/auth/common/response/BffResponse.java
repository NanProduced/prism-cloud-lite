package nan.produced.prism.auth.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import nan.produced.prism.auth.common.exception.ErrorCode;

/**
 * BFF/前端统一响应格式
 * <p>
 * 遵循 service-standards.md 第3.2节规范：
 * <pre>
 * {
 *   "success": false,
 *   "data": null,
 *   "error": {
 *     "code": "AUTH-1003",
 *     "message": "验证码错误",
 *     "displayMessage": "验证码不正确，请重新获取",
 *     "retryable": true
 *   },
 *   "traceId": "0af7651916cd43dd8448eb211c80319c"
 * }
 * </pre>
 *
 * @param <T> 数据类型
 * @author Nan
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BffResponse<T> {

    /**
     * 操作是否成功
     * true: 操作成功，data 字段有值
     * false: 操作失败，error 字段有值
     */
    private boolean success;

    /**
     * 响应数据（成功时有值）
     */
    private T data;

    /**
     * 错误详情（失败时有值）
     */
    private ErrorDetail error;

    /**
     * 链路追踪ID（Sleuth/Zipkin自动生成）
     */
    private String traceId;

    /**
     * 错误详情
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorDetail {

        /**
         * 错误码（格式：<领域>-<四位数字>）
         */
        private String code;

        /**
         * 技术向错误信息（用于开发调试）
         */
        private String message;

        /**
         * 用户友好的错误提示（用于前端显示）
         */
        private String displayMessage;

        /**
         * 是否可重试
         * true: 用户可以修正输入或等待后重试
         * false: 业务冲突或权限错误，重试无意义
         */
        private boolean retryable;
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 创建成功响应（无数据）
     */
    public static <T> BffResponse<T> success() {
        return success(null);
    }

    /**
     * 创建成功响应（带数据）
     * @param data 响应数据
     */
    public static <T> BffResponse<T> success(T data) {
        BffResponse<T> response = new BffResponse<>();
        response.setSuccess(true);
        response.setData(data);
        return response;
    }

    /**
     * 创建错误响应（从ErrorCode）
     * @param errorCode 错误码枚举
     */
    public static <T> BffResponse<T> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.getDisplayMessage());
    }

    /**
     * 创建错误响应（自定义displayMessage）
     * @param errorCode 错误码枚举
     * @param displayMessage 自定义用户提示
     */
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

    /**
     * 设置traceId（链式调用）
     * @param traceId 链路追踪ID
     * @return 当前响应对象
     */
    public BffResponse<T> withTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }
}
