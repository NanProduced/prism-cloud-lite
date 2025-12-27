package nan.produced.prism.gateway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String MDC_TRACE_ID = "traceId";
    private static final String MDC_SPAN_ID = "spanId";

    private static final String HEADER_TRACE_ID = "X-Trace-Id";
    private static final String HEADER_B3_TRACE_ID = "X-B3-TraceId";
    private static final String HEADER_B3_SPAN_ID = "X-B3-SpanId";
    private static final String HEADER_TRACEPARENT = "traceparent";

    private static final Pattern TRACEPARENT_PATTERN =
        Pattern.compile("(?i)^[\\da-f]{2}-([\\da-f]{32})-([\\da-f]{16})-[\\da-f]{2}.*$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        String existingTraceId = MDC.get(MDC_TRACE_ID);
        String existingSpanId = MDC.get(MDC_SPAN_ID);

        boolean addedTraceId = false;
        boolean addedSpanId = false;

        String traceId = StringUtils.hasText(existingTraceId) ? existingTraceId : resolveTraceId(request);
        if (!StringUtils.hasText(existingTraceId)) {
            MDC.put(MDC_TRACE_ID, traceId);
            addedTraceId = true;
        }

        String spanId = StringUtils.hasText(existingSpanId) ? existingSpanId : resolveSpanId(request);
        if (!StringUtils.hasText(existingSpanId)) {
            MDC.put(MDC_SPAN_ID, spanId);
            addedSpanId = true;
        }

        HttpServletRequest wrapped = new TraceHeaderRequestWrapper(request, traceId, spanId);
        try {
            filterChain.doFilter(wrapped, response);
        } finally {
            if (StringUtils.hasText(traceId)) {
                response.setHeader(HEADER_TRACE_ID, traceId);
            }
            if (addedSpanId && spanId.equals(MDC.get(MDC_SPAN_ID))) {
                MDC.remove(MDC_SPAN_ID);
            }
            if (addedTraceId && traceId.equals(MDC.get(MDC_TRACE_ID))) {
                MDC.remove(MDC_TRACE_ID);
            }
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        if (request == null) {
            return generateTraceId();
        }
        String direct = firstNonBlank(
            request.getHeader(HEADER_TRACE_ID),
            request.getHeader(HEADER_B3_TRACE_ID),
            parseTraceIdFromTraceparent(request.getHeader(HEADER_TRACEPARENT)),
            request.getHeader("X-Request-Id")
        );
        return normalizeTraceId(direct);
    }

    private String resolveSpanId(HttpServletRequest request) {
        if (request == null) {
            return generateSpanId();
        }
        String direct = firstNonBlank(
            request.getHeader(HEADER_B3_SPAN_ID),
            parseSpanIdFromTraceparent(request.getHeader(HEADER_TRACEPARENT))
        );
        return normalizeSpanId(direct);
    }

    private String parseTraceIdFromTraceparent(String traceparent) {
        if (!StringUtils.hasText(traceparent)) {
            return null;
        }
        Matcher m = TRACEPARENT_PATTERN.matcher(traceparent.trim());
        return m.matches() ? m.group(1) : null;
    }

    private String parseSpanIdFromTraceparent(String traceparent) {
        if (!StringUtils.hasText(traceparent)) {
            return null;
        }
        Matcher m = TRACEPARENT_PATTERN.matcher(traceparent.trim());
        return m.matches() ? m.group(2) : null;
    }

    private String normalizeTraceId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return generateTraceId();
        }
        String trimmed = raw.trim();
        String hex = trimmed.toLowerCase(Locale.ROOT).replace("-", "");
        if (hex.matches("^[0-9a-f]{16}$") || hex.matches("^[0-9a-f]{32}$")) {
            return hex;
        }
        return UUID.nameUUIDFromBytes(trimmed.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }

    private String normalizeSpanId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return generateSpanId();
        }
        String trimmed = raw.trim();
        String hex = trimmed.toLowerCase(Locale.ROOT).replace("-", "");
        if (hex.matches("^[0-9a-f]{16}$")) {
            return hex;
        }
        return UUID.nameUUIDFromBytes(trimmed.getBytes(StandardCharsets.UTF_8)).toString().replace("-", "").substring(0, 16);
    }

    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String c : candidates) {
            if (StringUtils.hasText(c)) {
                return c;
            }
        }
        return null;
    }

    private static class TraceHeaderRequestWrapper extends HttpServletRequestWrapper {
        private final String traceId;
        private final String spanId;

        TraceHeaderRequestWrapper(HttpServletRequest request, String traceId, String spanId) {
            super(request);
            this.traceId = traceId;
            this.spanId = spanId;
        }

        @Override
        public String getHeader(String name) {
            if (name == null) {
                return null;
            }
            if (HEADER_TRACE_ID.equalsIgnoreCase(name)) {
                return traceId;
            }
            if (HEADER_B3_TRACE_ID.equalsIgnoreCase(name)) {
                return traceId;
            }
            if (HEADER_B3_SPAN_ID.equalsIgnoreCase(name)) {
                return spanId;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (name == null) {
                return Collections.emptyEnumeration();
            }
            if (HEADER_TRACE_ID.equalsIgnoreCase(name) || HEADER_B3_TRACE_ID.equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(traceId));
            }
            if (HEADER_B3_SPAN_ID.equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(spanId));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            if (names.stream().noneMatch(n -> HEADER_TRACE_ID.equalsIgnoreCase(n))) {
                names.add(HEADER_TRACE_ID);
            }
            if (names.stream().noneMatch(n -> HEADER_B3_TRACE_ID.equalsIgnoreCase(n))) {
                names.add(HEADER_B3_TRACE_ID);
            }
            if (names.stream().noneMatch(n -> HEADER_B3_SPAN_ID.equalsIgnoreCase(n))) {
                names.add(HEADER_B3_SPAN_ID);
            }
            return Collections.enumeration(names);
        }
    }
}
