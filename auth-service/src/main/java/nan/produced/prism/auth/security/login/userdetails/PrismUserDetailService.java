package nan.produced.prism.auth.security.login.userdetails;

import java.util.Collection;
import java.util.List;

import lombok.RequiredArgsConstructor;

import nan.produced.prism.auth.domain.user.EndUserEntity;
import nan.produced.prism.auth.domain.user.LoginAliasEntity;
import nan.produced.prism.auth.domain.user.LoginAliasType;
import nan.produced.prism.auth.domain.user.UserType;
import nan.produced.prism.auth.domain.user.repository.LoginAliasRepository;
import nan.produced.prism.auth.security.principal.PrismUserPrincipal;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PrismUserDetailService implements UserDetailsService {

    private final LoginAliasRepository loginAliasRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalized = username == null ? "" : username.trim();
        if (normalized.isEmpty()) {
            throw new UsernameNotFoundException("identifier is blank");
        }

        EndUserEntity entity = switch (determineInputType(normalized)) {
            case LoginAliasType.EMAIL -> resolveByAlias(normalized, LoginAliasType.EMAIL);
            case LoginAliasType.PHONE -> resolveByAlias(normalized, LoginAliasType.PHONE);
        };

        return PrismUserPrincipal.fromEndUser(
                entity,
                defaultRoles(entity.getUserType()),
                null,
                null
        );
    }

    private EndUserEntity resolveByAlias(String value, LoginAliasType type) {
        return (type == null ? loginAliasRepository.findAnyByValue(value) : loginAliasRepository.findByValueAndType(value, type))
                .map(LoginAliasEntity::getUser)
                .orElseThrow(() -> notFound(value));
    }

    private UsernameNotFoundException notFound(String identifier) {
        return new UsernameNotFoundException("User not found for identifier " + identifier);
    }

    private Collection<String> defaultRoles(UserType userType) {
        return userType == UserType.ADMIN ? List.of("ROLE_ADMIN") : List.of("ROLE_END_USER");
    }

    private LoginAliasType determineInputType(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }

        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        String phoneRegex = "^1[3-9]\\d{9}$";

        if (input.matches(emailRegex)) {
            return LoginAliasType.EMAIL;
        } else if (input.matches(phoneRegex)) {
            return LoginAliasType.PHONE;
        } else {
            return null;
        }
    }
}
