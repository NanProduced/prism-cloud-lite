package nan.produced.prism.auth.security.oauth.google;

public record GoogleIdTokenClaims(
    String subject,
    String email,
    boolean emailVerified,
    String name,
    String picture
) {}

