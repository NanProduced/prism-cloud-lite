package nan.produced.prism.auth.security;

import java.util.Optional;
import nan.produced.prism.auth.security.principal.PrismUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;


public final class SecurityUtils {

    private SecurityUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 获取当前认证用户的安全主体信息
     *
     * @return 返回包含PrismUserPrincipal的Optional对象，如果未认证或不存在则返回空Optional
     */
    public static Optional<PrismUserPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof PrismUserPrincipal prismUserPrincipal) {
            return Optional.of(prismUserPrincipal);
        }
        return Optional.empty();
    }

    public static PrismUserPrincipal requirePrincipal() {
        return currentPrincipal()
                .orElseThrow(() -> new IllegalStateException("No authenticated Prism user present"));
    }
}
