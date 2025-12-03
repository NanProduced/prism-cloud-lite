package nan.produced.prism.core.common.util;

import org.slf4j.MDC;

/**
 * TraceId 工具类
 * 用于获取 Sleuth/Zipkin 自动生成的 traceId
 */
public class TraceUtils {

    private TraceUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 获取当前请求的 traceId
     * <p>
     * traceId 由 Spring Cloud Sleuth 自动生成并存储到 MDC 中
     *
     * @return traceId，如果不存在则返回 "unknown"
     */
    public static String getTraceId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId : "unknown";
    }
}
