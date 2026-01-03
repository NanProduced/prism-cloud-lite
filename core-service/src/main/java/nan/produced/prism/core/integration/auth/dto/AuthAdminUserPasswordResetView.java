package nan.produced.prism.core.integration.auth.dto;

public record AuthAdminUserPasswordResetView(
    String publicId,
    String userId,
    String newPassword
) {
}

