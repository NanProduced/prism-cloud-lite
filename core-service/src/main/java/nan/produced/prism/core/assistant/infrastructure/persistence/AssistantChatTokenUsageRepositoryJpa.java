package nan.produced.prism.core.assistant.infrastructure.persistence;

import nan.produced.prism.core.assistant.domain.AssistantChatTokenUsageEntity;
import nan.produced.prism.core.assistant.domain.AssistantChatTokenUsageId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssistantChatTokenUsageRepositoryJpa extends JpaRepository<AssistantChatTokenUsageEntity, AssistantChatTokenUsageId> {

    Optional<AssistantChatTokenUsageEntity> findByUserIdAndDay(UUID userId, LocalDate day);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AssistantChatTokenUsageEntity u
            SET u.usedTokens = u.usedTokens + :delta,
                u.updatedAt = CURRENT_TIMESTAMP
            WHERE u.userId = :userId AND u.day = :day
            """)
    int incrementUsedTokens(@Param("userId") UUID userId, @Param("day") LocalDate day, @Param("delta") long delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AssistantChatTokenUsageEntity u
            SET u.frozenTokens = u.frozenTokens + :delta,
                u.lastFrozenAt = CURRENT_TIMESTAMP,
                u.updatedAt = CURRENT_TIMESTAMP
            WHERE u.userId = :userId AND u.day = :day
            """)
    int incrementFrozenTokens(@Param("userId") UUID userId, @Param("day") LocalDate day, @Param("delta") long delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AssistantChatTokenUsageEntity u
            SET u.frozenTokens = GREATEST(0, u.frozenTokens - :delta),
                u.updatedAt = CURRENT_TIMESTAMP
            WHERE u.userId = :userId AND u.day = :day
            """)
    int decrementFrozenTokens(@Param("userId") UUID userId, @Param("day") LocalDate day, @Param("delta") long delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AssistantChatTokenUsageEntity u
            SET u.usedTokens = u.usedTokens + :usedDelta,
                u.frozenTokens = GREATEST(0, u.frozenTokens - :frozenDelta),
                u.updatedAt = CURRENT_TIMESTAMP
            WHERE u.userId = :userId AND u.day = :day
            """)
    int settleTokens(@Param("userId") UUID userId, @Param("day") LocalDate day,
                      @Param("usedDelta") long usedDelta, @Param("frozenDelta") long frozenDelta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE AssistantChatTokenUsageEntity u
            SET u.frozenTokens = 0,
                u.lastFrozenAt = NULL,
                u.updatedAt = CURRENT_TIMESTAMP
            WHERE u.frozenTokens > 0
              AND u.lastFrozenAt IS NOT NULL
              AND u.lastFrozenAt < :cutoff
            """)
    int resetExpiredFrozenTokens(@Param("cutoff") Instant cutoff);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            WITH current_state AS (
                SELECT
                    COALESCE(u.used_tokens, 0) AS used_tokens,
                    COALESCE((SELECT SUM(frozen_tokens) FROM assistant.assistant_chat_token_freeze WHERE user_id = :userId AND day = :day AND status = 'ACTIVE'), 0) AS active_frozen
                FROM assistant.assistant_chat_token_usage_daily u
                WHERE u.user_id = :userId AND u.day = :day
                UNION ALL
                SELECT 0, 0 WHERE NOT EXISTS (SELECT 1 FROM assistant.assistant_chat_token_usage_daily WHERE user_id = :userId AND day = :day)
            )
            INSERT INTO assistant.assistant_chat_token_freeze (
                user_id, day, req_id, frozen_tokens, status, created_at, updated_at
            )
            SELECT :userId, :day, :reqId, :tokensToFreeze, 'ACTIVE', NOW(), NOW()
            WHERE :dailyLimit <= 0
               OR (:dailyLimit > 0 AND (SELECT used_tokens + active_frozen + :tokensToFreeze FROM current_state) <= :dailyLimit)
            ON CONFLICT (req_id) DO NOTHING
            """, nativeQuery = true)
    int tryInsertFreezeRecord(
            @Param("userId") UUID userId,
            @Param("day") LocalDate day,
            @Param("reqId") UUID reqId,
            @Param("tokensToFreeze") long tokensToFreeze,
            @Param("dailyLimit") long dailyLimit);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO assistant.assistant_chat_token_usage_daily (
                user_id, day, used_tokens, frozen_tokens, last_frozen_at, updated_at
            ) VALUES (
                :userId, :day, 0, :tokensToFreeze, NOW(), NOW()
            )
            ON CONFLICT (user_id, day) DO UPDATE SET
                frozen_tokens = assistant.assistant_chat_token_usage_daily.frozen_tokens + :tokensToFreeze,
                last_frozen_at = NOW(),
                updated_at = NOW()
            """, nativeQuery = true)
    int incrementFrozenTokensNative(
            @Param("userId") UUID userId,
            @Param("day") LocalDate day,
            @Param("tokensToFreeze") long tokensToFreeze);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO assistant.assistant_chat_token_usage_daily (
                user_id, day, used_tokens, frozen_tokens, updated_at
            ) VALUES (
                :userId, :day, :actualUsed, :frozenToRelease * -1, NOW()
            )
            ON CONFLICT (user_id, day) DO UPDATE SET
                used_tokens = assistant.assistant_chat_token_usage_daily.used_tokens + :actualUsed,
                frozen_tokens = GREATEST(0, assistant.assistant_chat_token_usage_daily.frozen_tokens - :frozenToRelease),
                updated_at = NOW()
            """, nativeQuery = true)
    int settleTokensWithPositiveUsed(
            @Param("userId") UUID userId,
            @Param("day") LocalDate day,
            @Param("actualUsed") long actualUsed,
            @Param("frozenToRelease") long frozenToRelease);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE assistant.assistant_chat_token_usage_daily
            SET frozen_tokens = GREATEST(0, frozen_tokens - :frozenToRelease),
                updated_at = NOW()
            WHERE user_id = :userId AND day = :day
            """, nativeQuery = true)
    int releaseFrozenTokensFromSummary(
            @Param("userId") UUID userId,
            @Param("day") LocalDate day,
            @Param("frozenToRelease") long frozenToRelease);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE assistant.assistant_chat_token_usage_daily s
            SET frozen_tokens = GREATEST(0, frozen_tokens - (
                SELECT COALESCE(SUM(f.frozen_tokens), 0)
                FROM assistant.assistant_chat_token_freeze f
                WHERE f.user_id = s.user_id
                  AND f.day = s.day
                  AND f.status = 'RELEASED'
                  AND f.updated_at >= NOW() - INTERVAL '5 minutes'
            )),
            updated_at = NOW()
            WHERE s.frozen_tokens > 0
            """, nativeQuery = true)
    int updateSummaryForRecentlyReleased();
}
