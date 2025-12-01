package nan.produced.prism.gateway.security.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.gateway.security.config.GatewaySecurityProps;
import nan.produced.prism.gateway.utils.JsonUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;

/**
 * 自定义登录认证入口点，处理不同请求类型的认证失败响应
 * <p>
 * 处理逻辑：
 * <ul>
 *   <li>Ajax请求 (Accept: application/json 或 X-Requested-With: XMLHttpRequest) 返回401 Unauthorized</li>
 *   <li>OPTIONS请求 (跨域预检) 返回200 OK</li>
 *   <li>其他请求 (非Ajax、非OPTIONS) 执行OAuth2重定向登录</li>
 * </ul>
 * </p>
 *
 * @author Nan
 */
@Component
@RequiredArgsConstructor
public class GatewayAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final GatewaySecurityProps gatewaySecurityProps;

    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {

        // 1. 处理 OPTIONS 请求 (跨域预检) -> 返回 200
        if (HttpMethod.OPTIONS.name().equals(request.getMethod())) {
            response.setStatus(HttpStatus.OK.value());
            return;
        }

        // 2. 处理 Ajax/API 请求 -> 返回 401 JSON
        if (isAjaxRequest(request)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(JsonUtils.toJson(GatewayJsonResponse.builder()
                    .code("401")
                    .msg("Unauthorized")
                    .build()));
            return;
        }

        // 3. 其他请求 (普通页面访问) -> 重定向到 OAuth2 登录端点
        redirectStrategy.sendRedirect(request, response, gatewaySecurityProps.getOauth2().getLoginEndpoint());
    }

    /**
     * 判断是否为Ajax请求
     * @param request 请求
     * @return 是否为Ajax请求
     */
    private boolean isAjaxRequest(HttpServletRequest request) {

        // 检查 Accept 头是否包含 application/json (且排除 */*)
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (StringUtils.hasText(accept) && accept.contains(MediaType.APPLICATION_JSON_VALUE)) {
            return true;
        }

        // 检查 Content-Type 是否为 application/json
        String contentType = request.getContentType();
        if (StringUtils.hasText(contentType) && contentType.contains(MediaType.APPLICATION_JSON_VALUE)) {
            return true;
        }

        // 检查 X-Requested-With (jQuery 等库的标准头)
        String xRequestedWith = request.getHeader("X-Requested-With");
        return StringUtils.hasText(xRequestedWith) && "XMLHttpRequest".equals(xRequestedWith);
    }
}
