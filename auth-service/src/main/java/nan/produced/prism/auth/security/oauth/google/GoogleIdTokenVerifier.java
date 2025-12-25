package nan.produced.prism.auth.security.oauth.google;

import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.exception.ErrorCode;
import nan.produced.prism.auth.security.oauth.GoogleOAuthProps;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class GoogleIdTokenVerifier {

    private final GoogleOAuthProps props;

    private volatile JwtDecoder jwtDecoder;

    public GoogleIdTokenClaims verify(String idToken) {
        if (!StringUtils.hasText(idToken)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "idToken is required");
        }
        if (!StringUtils.hasText(props.getClientId())) {
            throw new BizException(ErrorCode.GOOGLE_LOGIN_NOT_CONFIGURED);
        }

        Jwt jwt;
        try {
            jwt = decoder().decode(idToken);
        } catch (JwtException ex) {
            throw new BizException(ErrorCode.GOOGLE_ID_TOKEN_INVALID, "invalid google id_token", ex);
        }

        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        Boolean emailVerified = jwt.getClaimAsBoolean("email_verified");
        String name = jwt.getClaimAsString("name");
        String picture = jwt.getClaimAsString("picture");

        if (!StringUtils.hasText(subject)) {
            throw new BizException(ErrorCode.GOOGLE_ID_TOKEN_INVALID, "google sub is missing");
        }
        if (!StringUtils.hasText(email)) {
            throw new BizException(ErrorCode.GOOGLE_ID_TOKEN_INVALID, "google email is missing");
        }
        if (!Boolean.TRUE.equals(emailVerified)) {
            throw new BizException(ErrorCode.GOOGLE_ID_TOKEN_INVALID, "google email is not verified");
        }

        return new GoogleIdTokenClaims(
            subject,
            email.trim().toLowerCase(Locale.ROOT),
            true,
            StringUtils.hasText(name) ? name.trim() : null,
            StringUtils.hasText(picture) ? picture.trim() : null
        );
    }

    private JwtDecoder decoder() {
        JwtDecoder current = jwtDecoder;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (jwtDecoder == null) {
                jwtDecoder = buildDecoder();
            }
            return jwtDecoder;
        }
    }

    private JwtDecoder buildDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(props.getJwkSetUri()).build();
        decoder.setJwtValidator(new CompositeValidator(
            List.of(
                new JwtTimestampValidator(),
                new IssuerValidator(props.getIssuers()),
                new AudienceValidator(props.getClientId())
            )
        ));
        return decoder;
    }

    private static final class CompositeValidator implements OAuth2TokenValidator<Jwt> {
        private final List<OAuth2TokenValidator<Jwt>> delegates;

        private CompositeValidator(List<OAuth2TokenValidator<Jwt>> delegates) {
            this.delegates = delegates;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            OAuth2TokenValidatorResult result = OAuth2TokenValidatorResult.success();
            for (OAuth2TokenValidator<Jwt> delegate : delegates) {
                if (delegate == null) {
                    continue;
                }
                OAuth2TokenValidatorResult delegateResult = delegate.validate(token);
                if (delegateResult.hasErrors()) {
                    return delegateResult;
                }
                result = delegateResult;
            }
            return result;
        }
    }

    private static final class IssuerValidator implements OAuth2TokenValidator<Jwt> {
        private final List<String> issuers;

        private IssuerValidator(List<String> issuers) {
            this.issuers = issuers == null ? List.of() : issuers;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            if (token == null || token.getIssuer() == null || !StringUtils.hasText(token.getIssuer().toString())) {
                return error("invalid_token", "issuer is missing");
            }
            String issuer = token.getIssuer().toString();
            for (String allowed : issuers) {
                if (StringUtils.hasText(allowed) && allowed.equalsIgnoreCase(issuer)) {
                    return OAuth2TokenValidatorResult.success();
                }
            }
            return error("invalid_token", "issuer is not allowed");
        }
    }

    private static final class AudienceValidator implements OAuth2TokenValidator<Jwt> {
        private final String clientId;

        private AudienceValidator(String clientId) {
            this.clientId = clientId;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            if (!StringUtils.hasText(clientId)) {
                return error("invalid_token", "google clientId is missing");
            }
            List<String> aud = token.getAudience();
            if (aud == null || aud.isEmpty()) {
                return error("invalid_token", "audience is missing");
            }
            for (String value : aud) {
                if (clientId.equals(value)) {
                    return OAuth2TokenValidatorResult.success();
                }
            }
            return error("invalid_token", "audience mismatch");
        }
    }

    private static OAuth2TokenValidatorResult error(String code, String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(code, description, null));
    }
}

