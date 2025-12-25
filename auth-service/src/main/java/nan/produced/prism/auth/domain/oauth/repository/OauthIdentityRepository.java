package nan.produced.prism.auth.domain.oauth.repository;

import java.util.Optional;
import nan.produced.prism.auth.domain.oauth.OauthIdentityEntity;
import nan.produced.prism.auth.domain.oauth.OauthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OauthIdentityRepository extends JpaRepository<OauthIdentityEntity, Long> {

    Optional<OauthIdentityEntity> findByProviderAndProviderSubject(OauthProvider provider, String providerSubject);

    Optional<OauthIdentityEntity> findByProviderAndUser_Id(OauthProvider provider, java.util.UUID userId);
}

