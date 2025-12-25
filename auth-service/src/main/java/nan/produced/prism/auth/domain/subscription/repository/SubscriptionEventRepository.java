package nan.produced.prism.auth.domain.subscription.repository;

import java.util.UUID;
import nan.produced.prism.auth.domain.subscription.SubscriptionEventEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubscriptionEventRepository extends JpaRepository<SubscriptionEventEntity, Long> {

    Page<SubscriptionEventEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}

