package nan.produced.prism.auth.security.otp;

/**
 * PNV（手机号短信验证码）使用场景，用于选择短信模板与隔离校验流程。
 */
public enum PnvScene {
    LOGIN,
    BIND,
    PASSWORD_RESET
}

