package nan.produced.prism.core.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.security.api.CloudAuthContext;
import nan.produced.prism.core.security.api.CloudAuthUser;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 过滤器：解析 CLOUD_AUTH 头并存储到 ThreadLocal
 * <p>
 * 执行顺序: HIGHEST_PRECEDENCE（最先执行）
 * <p>
 * 流程:
 * 1. 从请求头中提取 CLOUD_AUTH
 * 2. 如果缺失，返回 401 Unauthorized
 * 3. 解析 CLOUD_AUTH（Base64 + JSON）
 * 4. 存储用户信息到 CloudAuthContext（ThreadLocal）
 * 5. 执行后续过滤器链
 * 6. 最终清理 ThreadLocal（防止内存泄漏）
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class CloudAuthFilter implements Filter {

    private final CloudAuthHeaderParser parser;
    private final ObjectMapper objectMapper;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String requestUri = httpRequest.getRequestURI();

        // Swagger/OpenAPI 文档不依赖 CLOUD_AUTH：
        // - Gateway 的 Swagger UI 允许匿名访问（用于本地联调/查看文档）
        // - 若强制要求 CLOUD_AUTH，会导致 /v3/api-docs 被 401 拦截，Swagger UI 无法加载
        if (shouldBypassCloudAuth(requestUri)) {
            try {
                chain.doFilter(request, response);
            } finally {
                CloudAuthContext.clear();
            }
            return;
        }

        // 内部接口不走 CLOUD_AUTH（由服务签名校验过滤器鉴权），避免 internal 调用被 401 拦截。
        // 注意：internal 接口禁止承载面向用户的业务能力，仅用于微服务间只读/受控能力。
        if (requestUri != null && requestUri.startsWith("/internal/")) {
            try {
                chain.doFilter(request, response);
            } finally {
                CloudAuthContext.clear();
            }
            return;
        }

        String cloudAuthHeader = httpRequest.getHeader(CloudAuthHeaderParser.HEADER_NAME);

        // 如果没有 CLOUD_AUTH 头，返回 401（符合规范的统一响应格式）
        if (cloudAuthHeader == null || cloudAuthHeader.isBlank()) {
            log.warn("Request without CLOUD_AUTH header: {} {}",
                httpRequest.getMethod(), httpRequest.getRequestURI());

            ApiResponse<Void> errorResponse = ApiResponse.<Void>error(ErrorCode.UNAUTHORIZED, "Missing CLOUD_AUTH header")
                .withMeta(TraceUtils.getTraceId(), null, "core-service");

            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(httpResponse.getWriter(), errorResponse);
            return;
        }

        try {
            // 解析 CLOUD_AUTH 头
            CloudAuthUser user = parser.parse(cloudAuthHeader);

            // 存储到 ThreadLocal（供后续业务代码使用）
            CloudAuthContext.setCurrentUser(user);

            log.debug("Authenticated request from publicId: {} ({})",
                user.publicId(), httpRequest.getRequestURI());

            // 继续过滤器链
            chain.doFilter(request, response);

        } catch (InvalidCloudAuthException e) {
            log.error("Invalid CLOUD_AUTH header for request: {} {}",
                httpRequest.getMethod(), httpRequest.getRequestURI(), e);

            ApiResponse<Void> errorResponse = ApiResponse.<Void>error(
                    ErrorCode.INVALID_CLOUD_AUTH_HEADER,
                    e.getMessage())
                .withMeta(TraceUtils.getTraceId(), null, "core-service");

            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json;charset=UTF-8");
            objectMapper.writeValue(httpResponse.getWriter(), errorResponse);

        } finally {
            // 清理 ThreadLocal（防止内存泄漏）
            CloudAuthContext.clear();
        }
    }

    private boolean shouldBypassCloudAuth(String requestUri) {
        if (requestUri == null || requestUri.isBlank()) {
            return false;
        }
        return requestUri.startsWith("/swagger-ui")
                || requestUri.startsWith("/v3/api-docs");
    }
}
