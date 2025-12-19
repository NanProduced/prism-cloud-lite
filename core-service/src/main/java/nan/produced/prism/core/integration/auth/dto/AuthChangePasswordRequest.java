package nan.produced.prism.core.integration.auth.dto;

public record AuthChangePasswordRequest(
    String currentPassword,
    String newPassword
) {}

