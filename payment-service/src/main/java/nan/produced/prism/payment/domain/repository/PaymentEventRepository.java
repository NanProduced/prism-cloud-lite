package nan.produced.prism.payment.domain.repository;

import java.util.UUID;
import nan.produced.prism.payment.domain.model.PaymentEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentEventRepository extends JpaRepository<PaymentEventEntity, Long> {
}
