package nan.produced.prism.gateway.security.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.savedrequest.SimpleSavedRequest;
import org.springframework.util.StringUtils;

public class SaveRequestOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    /**
     * 前端传递的 redirect_uri 参数
     */
    private static final String REDIRECT_URI_PARAM_NAME = "redirect_uri";

    /**
     * WebSession的savedRequest属性名
     */
    private static final String SAVED_REQUEST_ATTR = "SPRING_SECURITY_SAVED_REQUEST";

    public SaveRequestOAuth2AuthorizationRequestResolver(ClientRegistrationRepository  repository) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(repository, "/oauth2/authorization/");
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        OAuth2AuthorizationRequest authorizationRequest = delegate.resolve(request);
        saveRedirectUri(request, authorizationRequest);
        return authorizationRequest;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        OAuth2AuthorizationRequest authorizationRequest = delegate.resolve(request, clientRegistrationId);
        saveRedirectUri(request, authorizationRequest);
        return authorizationRequest;
    }

    private void saveRedirectUri(HttpServletRequest request, OAuth2AuthorizationRequest authorizationRequest) {
        if (authorizationRequest == null) return;

        String redirectUri = request.getParameter(REDIRECT_URI_PARAM_NAME);
        if (StringUtils.hasText(redirectUri)) {
            HttpSession session = request.getSession();
            SimpleSavedRequest savedRequest = new SimpleSavedRequest(redirectUri);
            session.setAttribute(SAVED_REQUEST_ATTR, savedRequest);
        }
    }
}
