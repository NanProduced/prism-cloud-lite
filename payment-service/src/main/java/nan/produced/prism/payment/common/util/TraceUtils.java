package nan.produced.prism.payment.common.util;

import org.slf4j.MDC;

public final class TraceUtils {

    private TraceUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static String getTraceId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId : "unknown";
    }
}
