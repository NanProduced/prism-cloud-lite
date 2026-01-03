package nan.produced.prism.core.integration.auth.dto;

public record AuthAdminUserCreateRequest(
    String username,
    String password
) {
}

