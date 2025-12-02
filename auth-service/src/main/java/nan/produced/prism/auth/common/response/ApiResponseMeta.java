package nan.produced.prism.auth.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API 响应元数据
 * 包含链路追踪和性能指标信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponseMeta {

    /**
     * 链路追踪 ID（来自 Spring Cloud Sleuth）
     * 用于关联日志和追踪
     */
    private String traceId;

    /**
     * 请求耗时（毫秒）
     */
    private Long durationMs;

    /**
     * 调用来源服务标识
     * 例如：auth-service、core-service
     */
    private String from;
}
