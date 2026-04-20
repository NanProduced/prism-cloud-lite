package nan.produced.prism.payment.domain.repository;

import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.payment.domain.model.PaymentSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentSubscriptionRepository extends JpaRepository<PaymentSubscriptionEntity, UUID> {

    Optional<PaymentSubscriptionEntity> findByExternalSubscriptionId(String externalSubscriptionId);

    Optional<PaymentSubscriptionEntity> findByUserId(UUID userId);
}
