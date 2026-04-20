package nan.produced.prism.payment.domain.repository;

import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.payment.domain.model.PaymentOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrderEntity, UUID> {

    Optional<PaymentOrderEntity> findByOrderNo(String orderNo);

    Optional<PaymentOrderEntity> findByExternalOrderNo(String externalOrderNo);
}
