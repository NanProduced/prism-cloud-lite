package nan.produced.prism.core.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import nan.produced.prism.core.common.exception.ErrorCode;

/**
 * BFF/前端统一响应格式
 * <p>
 * 遵循 service-standards.md 第3.2节规范：
 * 用于REST接口返回给前端客户端（Web浏览器、移动APP）
 * <p>
 * 与 RPC/Feign 调用返回的 ApiResponse 格式区分：
 * - ApiResponse：用于微服务间通信（含meta字段）
 * - BffResponse：用于返回给前端（含success布尔和error对象）
 * <p>
 * 成功响应示例：
 * <pre>
 * {
 *   "success": true,
 *   "data": {...},
 *   "error": null,
 *   "traceId": "0af7651916cd43dd8448eb211c80319c"
 * }
 * </pre>
 * <p>
 * 失败响应示例：
 * <pre>
 * {
 *   "success": false,
 *   "data": null,
 *   "error": {
 *     "code": "CORE-2401",
 *     "message": "未授权访问",
 *     "displayMessage": "请提供有效的认证信息",
 *     "retryable": false,
 *     "details": {}
 *   },
 *   "traceId": "0af7651916cd43dd8448eb211c80319c"
 * }
 * </pre>
 *
 * @param <T> 数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BffResponse<T> {

    /**
     * 操作是否成功
     * - true: 操作成功，data 字段有值
     * - false: 操作失败，error 字段有值
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
     * 用于跨服务追踪和日志关联
     */
    private String traceId;

    /**
     * 错误详情内部类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDetail {

        /**
         * 错误码（格式：&lt;领域&gt;-&lt;四位数字&gt;）
         * 例如：CORE-2401, AUTH-1003
         */
        private String code;

        /**
         * 技术向错误信息（用于开发调试）
         * 详细的技术错误信息，方便开发人员排查问题
         */
        private String message;

        /**
         * 用户友好的错误提示（用于前端显示）
         * 由国际化处理，向最终用户展示
         */
        private String displayMessage;

        /**
         * 是否可重试
         * - true: 用户可以修正输入或等待后重试（如网络超时）
         * - false: 业务冲突或权限错误，重试无意义（如用户已存在）
         */
        private boolean retryable;

        /**
         * 可选：结构化错误详情（用于前端精确引导/渲染）。
         * <p>例如：删除被引用阻断时返回引用列表/计数等。</p>
         */
        private Object details;
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
     *
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
     *
     * @param errorCode 错误码枚举
     */
    public static <T> BffResponse<T> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.getDisplayMessage());
    }

    /**
     * 创建错误响应（自定义displayMessage）
     *
     * @param errorCode       错误码枚举
     * @param displayMessage  自定义用户提示
     */
    public static <T> BffResponse<T> error(ErrorCode errorCode, String displayMessage) {
        BffResponse<T> response = new BffResponse<>();
        response.setSuccess(false);
        response.setError(new ErrorDetail(
            errorCode.getCode(),
            errorCode.getMessage(),
            displayMessage,
            errorCode.isRetryable(),
            null
        ));
        return response;
    }

    /**
     * 创建错误响应（自定义displayMessage + 结构化 details）
     */
    public static <T> BffResponse<T> error(ErrorCode errorCode, String displayMessage, Object details) {
        BffResponse<T> response = error(errorCode, displayMessage);
        if (response.getError() != null) {
            response.getError().setDetails(details);
        }
        return response;
    }

    /**
     * 设置traceId（链式调用）
     *
     * @param traceId 链路追踪ID
     * @return 当前响应对象
     */
    public BffResponse<T> withTraceId(String traceId) {
        this.traceId = traceId;
        return this;
    }
}
