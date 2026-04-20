package nan.produced.prism.payment.domain.repository;

import java.util.Optional;
import nan.produced.prism.payment.domain.model.WebhookEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEventEntity, Long> {

    boolean existsByEventId(String eventId);

    Optional<WebhookEventEntity> findByEventId(String eventId);

    @Modifying
    @Query("UPDATE WebhookEventEntity w SET w.processed = true, w.processedAt = CURRENT_TIMESTAMP WHERE w.eventId = :eventId")
    int markProcessed(@Param("eventId") String eventId);
}
