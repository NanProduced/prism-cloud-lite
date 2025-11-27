package nan.produced.prism.auth.security.principal;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
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
    private String displayName;
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
                .displayName(entity.getDisplayName())
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
                .displayName(entity.getDisplayName())
                .status(entity.getStatus())
                .userType(entity.getUserType())
                .password(entity.getPasswordHash())
                .authorities(toAuthorities(roles))
                .build();
    }

    public AuthClaims claims() {
        return new AuthClaims(
                publicId,
                displayName,
                userType,
                authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet()),
                subscriptionTier,
                subscriptionExpiresAt);
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

    private static Collection<? extends GrantedAuthority> toAuthorities(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        return roles.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(SimpleGrantedAuthority::new)
                .toList();
    }
}
