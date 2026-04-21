package nan.produced.prism.core.assistant.infrastructure.persistence;

import nan.produced.prism.core.assistant.domain.AssistantChatTokenFreezeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssistantChatTokenFreezeRepository extends JpaRepository<AssistantChatTokenFreezeEntity, UUID> {

    Optional<AssistantChatTokenFreezeEntity> findByReqId(UUID reqId);

    @Query("SELECT COALESCE(SUM(f.frozenTokens), 0) FROM AssistantChatTokenFreezeEntity f WHERE f.userId = :userId AND f.day = :day AND f.status = 'ACTIVE'")
    long sumActiveFrozenTokensByUserIdAndDay(@Param("userId") UUID userId, @Param("day") LocalDate day);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AssistantChatTokenFreezeEntity f SET f.status = 'SETTLED', f.updatedAt = CURRENT_TIMESTAMP WHERE f.reqId = :reqId AND f.status = 'ACTIVE'")
    int markAsSettledByReqId(@Param("reqId") UUID reqId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AssistantChatTokenFreezeEntity f SET f.status = 'RELEASED', f.updatedAt = CURRENT_TIMESTAMP WHERE f.reqId = :reqId AND f.status = 'ACTIVE'")
    int markAsReleasedByReqId(@Param("reqId") UUID reqId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AssistantChatTokenFreezeEntity f SET f.status = 'RELEASED', f.updatedAt = CURRENT_TIMESTAMP WHERE f.status = 'ACTIVE' AND f.createdAt < :cutoff")
    int markAsReleasedForExpired(@Param("cutoff") Instant cutoff);

    List<AssistantChatTokenFreezeEntity> findByUserIdAndDayAndStatus(UUID userId, LocalDate day, String status);

    List<AssistantChatTokenFreezeEntity> findByStatusAndCreatedAtBefore(String status, Instant cutoff);
}
