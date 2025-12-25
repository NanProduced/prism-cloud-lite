package nan.produced.prism.auth.domain.subscription.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.auth.domain.subscription.RedeemCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RedeemCodeRepository extends JpaRepository<RedeemCodeEntity, UUID> {

    Optional<RedeemCodeEntity> findByCode(String code);

    @Modifying
    @Query("""
        update RedeemCodeEntity c
           set c.redeemedBy = :userId,
               c.redeemedAt = :now
         where c.id = :id
           and c.enabled = true
           and c.redeemedAt is null
           and (c.expiresAt is null or c.expiresAt > :now)
        """)
    int markRedeemed(@Param("id") UUID id,
                     @Param("userId") UUID userId,
                     @Param("now") Instant now);
}

