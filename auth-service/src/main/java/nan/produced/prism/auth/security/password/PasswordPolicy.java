package nan.produced.prism.auth.security.password;

import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.exception.ErrorCode;

/**
 * Password policy shared by registration and account security flows.
 */
public final class PasswordPolicy {

    private PasswordPolicy() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Validates password strength and throws {@link BizException} when it violates policy.
     * <p>
     * Rules (current baseline):
     * - length >= 8
     * - contains at least one uppercase letter
     * - contains at least one lowercase letter
     * - contains at least one digit
     * </p>
     */
    public static void validateOrThrow(String password) {
        if (password == null || password.length() < 8) {
            throw new BizException(ErrorCode.INVALID_PASSWORD, "密码长度至少为8位");
        }

        if (!password.matches(".*[A-Z].*")) {
            throw new BizException(ErrorCode.INVALID_PASSWORD, "密码必须包含至少一个大写字母");
        }

        if (!password.matches(".*[a-z].*")) {
            throw new BizException(ErrorCode.INVALID_PASSWORD, "密码必须包含至少一个小写字母");
        }

        if (!password.matches(".*\\d.*")) {
            throw new BizException(ErrorCode.INVALID_PASSWORD, "密码必须包含至少一个数字");
        }
    }
}

