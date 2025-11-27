package nan.produced.prism.auth.security.login;

public enum LoginAuthType {
    PHONE_PWD,
    PHONE_OTP,
    EMAIL_PWD,
    EMAIL_OTP;

    public static LoginAuthType fromString(String value) {
        for (LoginAuthType type : values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }
}
