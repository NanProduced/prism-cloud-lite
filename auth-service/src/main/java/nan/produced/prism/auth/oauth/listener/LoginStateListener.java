package nan.produced.prism.auth.oauth.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.auth.oauth.authorization.OidcAuthorizationService;
import nan.produced.prism.auth.oauth.oidc.OidcLoginState;
import org.springframework.context.ApplicationListener;
import org.springframework.security.core.session.SessionDestroyedEvent;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.stereotype.Component;

import static nan.produced.prism.auth.oauth.oidc.OidcClaimsConstant.CLAIM_LOGIN_STATE;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginStateListener implements ApplicationListener<SessionDestroyedEvent> {

    private final OidcAuthorizationService authorizationService;

    @Override
    public void onApplicationEvent(SessionDestroyedEvent event) {
        String sessionId = event.getId();
        authorizationService.findBySessionId(sessionId)
                .forEach(authorization -> {
                    Integer loginState = authorization.getAttribute(CLAIM_LOGIN_STATE);
                    if (!OidcLoginState.LOGOUT.getCode().equals(loginState)) {
                        OAuth2Authorization updated = OAuth2Authorization.from(authorization)
                                .attribute(CLAIM_LOGIN_STATE, OidcLoginState.EXPIRED.getCode())
                                .build();
                        authorizationService.save(updated);
                        log.debug("OIDC - session_id: {} has expired, principal name:{}", sessionId, authorization.getPrincipalName());
                    }
                });
    }
}
