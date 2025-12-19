package nan.produced.prism.auth.domain.session.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.auth.domain.session.RememberMeTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository managing persistent remember-me tokens per device.
 */
public interface RememberMeTokenRepository extends JpaRepository<RememberMeTokenEntity, Long> {

    Optional<RememberMeTokenEntity> findBySeriesAndRevokedFalse(String series);

    Optional<RememberMeTokenEntity> findBySeries(String series);

    List<RememberMeTokenEntity> findByUserIdAndRevokedFalseOrderByCreatedAtDesc(UUID userId);

    @Modifying(clearAutomatically = true)
    @Query("update RememberMeTokenEntity t set t.revoked = true, t.revokedAt = :revokedAt where t.userId = :userId and t.revoked = false")
    int revokeAll(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);
}
