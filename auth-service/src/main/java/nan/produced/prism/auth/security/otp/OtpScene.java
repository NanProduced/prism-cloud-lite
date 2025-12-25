package nan.produced.prism.auth.security.otp;

/**
 * OTP 使用场景（用于隔离不同业务流程的验证码）。
 */
public enum OtpScene {
    REGISTRATION,
    LOGIN,
    PASSWORD_RESET
}

