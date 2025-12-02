package nan.produced.prism.auth.utils;

import org.slf4j.MDC;

public class TraceUtils {

    private TraceUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static String getTraceId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId : "unknown";
    }
}
