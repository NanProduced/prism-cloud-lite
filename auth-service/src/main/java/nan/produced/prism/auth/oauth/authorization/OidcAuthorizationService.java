package nan.produced.prism.auth.oauth.authorization;

import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;

import java.util.List;

public interface OidcAuthorizationService extends OAuth2AuthorizationService {

    OAuth2Authorization findByIdToken(String idToken);

    List<OAuth2Authorization> findBySessionId(String sessionId);
}
