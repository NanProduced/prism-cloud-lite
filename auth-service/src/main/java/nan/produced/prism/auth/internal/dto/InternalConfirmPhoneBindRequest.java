package nan.produced.prism.auth.internal.dto;

public record InternalConfirmPhoneBindRequest(
    String phone,
    String code
) {}

