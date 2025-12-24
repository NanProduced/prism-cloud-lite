package nan.produced.prism.core.message.infrastructure.persistence;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.core.message.domain.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface MessageRepositoryJpa extends JpaRepository<MessageEntity, UUID>, JpaSpecificationExecutor<MessageEntity> {

    Optional<MessageEntity> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndReadAtIsNull(UUID userId);

    @Transactional
    @Modifying
    @Query("""
        UPDATE MessageEntity m
           SET m.readAt = :readAt,
               m.updatedAt = :readAt
         WHERE m.userId = :userId
           AND m.id IN :ids
           AND m.readAt IS NULL
        """)
    int markRead(@Param("userId") UUID userId,
                 @Param("ids") Collection<UUID> ids,
                 @Param("readAt") OffsetDateTime readAt);

    @Transactional
    @Modifying
    @Query("""
        DELETE FROM MessageEntity m
         WHERE m.createdAt < :cutoff
        """)
    int deleteExpired(@Param("cutoff") OffsetDateTime cutoff);
}

