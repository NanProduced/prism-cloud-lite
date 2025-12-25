package nan.produced.prism.auth.domain.subscription.repository;

import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.auth.domain.subscription.UserSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscriptionEntity, UUID> {

    Optional<UserSubscriptionEntity> findByUserId(UUID userId);
}

