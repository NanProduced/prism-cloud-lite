package nan.produced.prism.auth.security.oauth.google;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.exception.ErrorCode;
import nan.produced.prism.auth.common.util.PublicIdGenerator;
import nan.produced.prism.auth.domain.oauth.OauthIdentityEntity;
import nan.produced.prism.auth.domain.oauth.OauthProvider;
import nan.produced.prism.auth.domain.oauth.repository.OauthIdentityRepository;
import nan.produced.prism.auth.domain.user.EndUserEntity;
import nan.produced.prism.auth.domain.user.LoginAliasEntity;
import nan.produced.prism.auth.domain.user.LoginAliasType;
import nan.produced.prism.auth.domain.user.UserStatus;
import nan.produced.prism.auth.domain.user.repository.EndUserRepository;
import nan.produced.prism.auth.domain.user.repository.LoginAliasRepository;
import nan.produced.prism.auth.security.principal.PrismUserPrincipal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Google三方登录服务
 *
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleLoginService {

    private final GoogleIdTokenVerifier tokenVerifier;
    private final OauthIdentityRepository oauthIdentityRepository;
    private final EndUserRepository endUserRepository;
    private final LoginAliasRepository loginAliasRepository;

    @Transactional
    public PrismUserPrincipal login(String idToken) {
        GoogleIdTokenClaims claims = tokenVerifier.verify(idToken);

        OauthIdentityEntity identity = oauthIdentityRepository
            .findByProviderAndProviderSubject(OauthProvider.GOOGLE, claims.subject())
            .orElse(null);

        EndUserEntity user;
        if (identity != null) {
            user = identity.getUser();
            updateIdentity(identity, claims);
        } else {
            user = endUserRepository.findByEmailIgnoreCase(claims.email()).orElse(null);
            if (user != null) {
                // 账号已绑定其他 Google（或未来其他 provider）
                oauthIdentityRepository.findByProviderAndUser_Id(OauthProvider.GOOGLE, user.getId())
                    .ifPresent(existing -> {
                        throw new BizException(ErrorCode.GOOGLE_ACCOUNT_ALREADY_BOUND);
                    });
            } else {
                user = createUser(claims.email());
            }

            identity = new OauthIdentityEntity();
            identity.setUser(user);
            identity.setProvider(OauthProvider.GOOGLE);
            identity.setProviderSubject(claims.subject());
            updateIdentity(identity, claims);

            try {
                oauthIdentityRepository.save(identity);
            } catch (DataIntegrityViolationException ex) {
                throw new BizException(ErrorCode.GOOGLE_ACCOUNT_ALREADY_BOUND, "google account already bound", ex);
            }
        }

        if (user == null || user.getId() == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        if (user.getStatus() == UserStatus.LOCKED) {
            throw new BizException(ErrorCode.INVALID_CREDENTIALS, "account is locked");
        }

        ensureEmailAlias(user, claims.email());
        return PrismUserPrincipal.fromEndUser(user, List.of("ROLE_END_USER"), null, null);
    }

    private void updateIdentity(OauthIdentityEntity identity, GoogleIdTokenClaims claims) {
        if (identity == null || claims == null) {
            return;
        }
        identity.setEmail(claims.email());
        identity.setEmailVerified(claims.emailVerified());
        identity.setDisplayName(claims.name());
        identity.setAvatarUrl(claims.picture());
    }

    private EndUserEntity createUser(String email) {
        if (!StringUtils.hasText(email)) {
            throw new BizException(ErrorCode.INVALID_PARAMETER, "email is required");
        }
        EndUserEntity user = new EndUserEntity();
        user.setEmail(email);
        user.setPasswordHash(null);
        user.setPublicId(PublicIdGenerator.generate());
        user.setMetadata("{}");
        try {
            return endUserRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            // 并发创建时重试查询即可
            return endUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BizException(ErrorCode.INTERNAL_SERVER_ERROR, "failed to create user", ex));
        }
    }

    private void ensureEmailAlias(EndUserEntity user, String email) {
        if (user == null || user.getId() == null || !StringUtils.hasText(email)) {
            return;
        }
        if (loginAliasRepository.findByValueAndType(email, LoginAliasType.EMAIL).isPresent()) {
            return;
        }
        LoginAliasEntity alias = new LoginAliasEntity();
        alias.setUser(user);
        alias.setAliasType(LoginAliasType.EMAIL);
        alias.setAliasValue(email);
        try {
            loginAliasRepository.save(alias);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Failed to create email alias for google login: {}", email, ex);
        }
    }
}

