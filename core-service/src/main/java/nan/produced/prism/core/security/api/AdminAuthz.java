package nan.produced.prism.core.security.api;

import nan.produced.prism.core.common.exception.AuthException;
import nan.produced.prism.core.common.exception.ErrorCode;

public final class AdminAuthz {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private AdminAuthz() {
        throw new IllegalStateException("AdminAuthz is a utility class and cannot be instantiated");
    }

    public static void requireAdmin() {
        CloudAuthUser user = CloudAuthContext.getCurrentUser();
        if (user.roles() == null || !user.roles().contains(ROLE_ADMIN)) {
            throw new AuthException(ErrorCode.FORBIDDEN, "Admin role required");
        }
    }
}

