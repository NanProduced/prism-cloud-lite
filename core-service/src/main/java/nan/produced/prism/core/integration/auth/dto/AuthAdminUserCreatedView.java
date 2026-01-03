package nan.produced.prism.core.integration.auth.dto;

public record AuthAdminUserCreatedView(
    String publicId,
    String userId,
    String username,
    String initialPassword
) {
}

