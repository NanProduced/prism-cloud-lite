package nan.produced.prism.auth.oauth.slo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.auth.security.SecurityProps;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import static org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames.STATE;

/**
 * OIDC RP-Initiated Logout 失败时的兜底处理：
 * <p>
 * 失败通常来自请求参数校验（例如 id_token_hint 无效、post_logout_redirect_uri 不在白名单等）。
 * 默认实现会返回 400 并落到 /error（浏览器看到 Whitelabel Error Page）。
 * <p>
 * 为了避免用户卡死在 OP（auth-service）错误页，这里统一重定向回网关的 logout-status。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OidcLogoutErrorRedirectHandler implements AuthenticationFailureHandler {

    private final SecurityProps securityProps;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) {
        String requested = request != null ? request.getParameter("post_logout_redirect_uri") : null;
        String state = request != null ? request.getParameter(STATE) : null;

        List<String> allowlist = securityProps.getOauth2().getClient().getPrismGatewayClient().resolvePostLogoutRedirectUris();
        String target;
        if (StringUtils.hasText(requested) && allowlist.contains(requested)) {
            target = requested;
        }
        else if (allowlist != null && !allowlist.isEmpty() && StringUtils.hasText(allowlist.getFirst())) {
            target = allowlist.getFirst();
        }
        else {
            target = securityProps.getSlo().getDefaultLogoutRedirectUri();
        }

        String finalTarget = StringUtils.hasText(state) ? String.format("%s?state=%s", target, state) : target;
        log.warn("OIDC logout request rejected, redirecting to {} (reason: {})",
            finalTarget, exception != null ? exception.getMessage() : "unknown");

        try {
            response.setStatus(HttpServletResponse.SC_FOUND);
            response.setHeader("Location", finalTarget);
        } catch (Exception ex) {
            // If even redirect fails, fall back to a minimal response to avoid container error pages.
            try {
                response.sendError(HttpServletResponse.SC_FOUND);
            } catch (Exception ignore) {
                // ignored
            }
        }
    }
}

