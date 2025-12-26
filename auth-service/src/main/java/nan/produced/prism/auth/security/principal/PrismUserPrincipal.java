package nan.produced.prism.auth.security.principal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nan.produced.prism.auth.domain.user.AdminUserEntity;
import nan.produced.prism.auth.domain.user.EndUserEntity;
import nan.produced.prism.auth.domain.user.UserStatus;
import nan.produced.prism.auth.domain.user.UserType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Unified {@link UserDetails} representation shared by admin and end-user accounts.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public final class PrismUserPrincipal implements UserDetails {

    private UUID id;
    private String publicId;
    private String email;
    private String phone;
    private UserStatus status;
    private UserType userType;
    private String password;
    private Collection<? extends GrantedAuthority> authorities;
    private String subscriptionTier;
    private Instant subscriptionExpiresAt;

    public static PrismUserPrincipal fromEndUser(EndUserEntity entity,
                                                 Collection<String> roles,
                                                 String subscriptionTier,
                                                 Instant subscriptionExpiresAt) {
        return PrismUserPrincipal.builder()
                .id(entity.getId())
                .publicId(entity.getPublicId())
                .email(entity.getEmail())
                .phone(entity.getPhone())
                .status(entity.getStatus())
                .userType(entity.getUserType())
                .password(entity.getPasswordHash())
                .authorities(toAuthorities(roles))
                .subscriptionTier(subscriptionTier)
                .subscriptionExpiresAt(subscriptionExpiresAt)
                .build();
    }

    public static PrismUserPrincipal fromAdmin(AdminUserEntity entity,
                                               Collection<String> roles) {
        return PrismUserPrincipal.builder()
                .id(entity.getId())
                .publicId(entity.getPublicId())
                .email(entity.getEmail())
                .phone(entity.getPhone())
                .status(entity.getStatus())
                .userType(entity.getUserType())
                .password(entity.getPasswordHash())
                .authorities(toAuthorities(roles))
                .build();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return publicId;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.LOCKED;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }

    /**
     * 将角色字符串集合转换为 GrantedAuthority 集合。
     * <p>
     * 注意：使用 {@code Collectors.toList()} 而非 {@code .toList()}，
     * 因为后者返回 {@code ImmutableCollections$ListN}，该类型不在
     * Spring Security Jackson 白名单中，会导致 OAuth2 授权信息
     * 序列化/反序列化失败。
     * </p>
     */
    private static Collection<? extends GrantedAuthority> toAuthorities(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return new ArrayList<>();
        }
        return roles.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
