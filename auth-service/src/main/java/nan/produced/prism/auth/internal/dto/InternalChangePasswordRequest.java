package nan.produced.prism.auth.internal.dto;

public record InternalChangePasswordRequest(
    String currentPassword,
    String newPassword
) {}

