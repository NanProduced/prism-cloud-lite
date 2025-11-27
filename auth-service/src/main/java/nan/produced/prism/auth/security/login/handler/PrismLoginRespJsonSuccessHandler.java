package nan.produced.prism.auth.security.login.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.auth.utils.HttpUtils;
import nan.produced.prism.auth.utils.JsonUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

import java.io.IOException;
import java.util.Optional;

@Slf4j
public class PrismLoginRespJsonSuccessHandler implements AuthenticationSuccessHandler {

    private final HttpSessionSecurityContextRepository repository = new HttpSessionSecurityContextRepository();

    private final RequestCache requestCache;

    public PrismLoginRespJsonSuccessHandler(RequestCache requestCache) {
        this.requestCache = requestCache;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        log.debug("Login success - principal: {}", authentication.getPrincipal());
        clearAuthenticationAttributes(request);

        String redirectUri = Optional.ofNullable(requestCache.getRequest(request, response))
                .map(SavedRequest::getRedirectUrl)
                .orElse("/");

        String xrw = request .getHeader("X-Requested-With");
        String accept = request.getHeader("Accept");
        boolean isAjax = "XMLHttpRequest".equalsIgnoreCase(xrw) || (accept != null && accept.contains("application/json"));

        if (isAjax) {
            String respJson = JsonUtils.toJson(PrismLoginRespJson.builder()
                    .code("200")
                    .redirectUrl(redirectUri)
                    .build());
            HttpUtils.responseJson(respJson, response);
        }
        else {
            repository.saveContext(SecurityContextHolder.getContext(), request, response);
            response.sendRedirect(redirectUri);
        }
    }

    /**
     * 清理登录过程中可能产生的认证错误信息，确保用户登录成功后不会保留之前的异常数据
     *
     * @param request 请求
     */
    protected final void clearAuthenticationAttributes(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
        }
    }
}
