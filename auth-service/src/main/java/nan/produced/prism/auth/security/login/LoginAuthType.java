package nan.produced.prism.auth.security.login;

public enum LoginAuthType {
    PHONE_PWD,
    PHONE_OTP,
    EMAIL_PWD,
    EMAIL_OTP,
    /**
     * Management console login (admin/manager) using email + password.
     * <p>
     * Console does not support OTP/Google login and does not share the end-user login alias system.
     * </p>
     */
    ADMIN_EMAIL_PWD;

    public static LoginAuthType fromString(String value) {
        for (LoginAuthType type : values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }
}
