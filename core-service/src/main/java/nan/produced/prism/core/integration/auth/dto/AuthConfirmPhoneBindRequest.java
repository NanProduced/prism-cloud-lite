package nan.produced.prism.core.integration.auth.dto;

public record AuthConfirmPhoneBindRequest(
    String phone,
    String code
) {}

