package nan.produced.prism.core.assistant.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class AssistantChatTokenUsageRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public record FreezeRecord(
            UUID id,
            UUID userId,
            LocalDate day,
            UUID reqId,
            long frozenTokens,
            String status,
            java.time.Instant createdAt,
            java.time.Instant updatedAt
    ) {}

    public long getUsedTokens(UUID userId, LocalDate day) {
        try {
            Long v = jdbcTemplate.getJdbcTemplate().queryForObject(
                    "SELECT used_tokens FROM assistant.assistant_chat_token_usage_daily WHERE user_id = ? AND day = ?",
                    Long.class,
                    userId,
                    day
            );
            return v != null ? v : 0L;
        } catch (EmptyResultDataAccessException ignored) {
            return 0L;
        }
    }

    public long getFrozenTokensFromSummary(UUID userId, LocalDate day) {
        try {
            Long v = jdbcTemplate.getJdbcTemplate().queryForObject(
                    "SELECT frozen_tokens FROM assistant.assistant_chat_token_usage_daily WHERE user_id = ? AND day = ?",
                    Long.class,
                    userId,
                    day
            );
            return v != null ? v : 0L;
        } catch (EmptyResultDataAccessException ignored) {
            return 0L;
        }
    }

    public long getActiveFrozenTokens(UUID userId, LocalDate day) {
        try {
            Long v = jdbcTemplate.getJdbcTemplate().queryForObject(
                    "SELECT COALESCE(SUM(frozen_tokens), 0) FROM assistant.assistant_chat_token_freeze WHERE user_id = ? AND day = ? AND status = 'ACTIVE'",
                    Long.class,
                    userId,
                    day
            );
            return v != null ? v : 0L;
        } catch (EmptyResultDataAccessException ignored) {
            return 0L;
        }
    }

    public long getTotalCommittedAndFrozen(UUID userId, LocalDate day) {
        return getUsedTokens(userId, day) + getActiveFrozenTokens(userId, day);
    }

    public Optional<FreezeRecord> findFreezeRecordByReqId(UUID reqId) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT id, user_id, day, req_id, frozen_tokens, status, created_at, updated_at
                    FROM assistant.assistant_chat_token_freeze
                    WHERE req_id = :reqId
                    """,
                    new MapSqlParameterSource().addValue("reqId", reqId),
                    (rs, rowNum) -> new FreezeRecord(
                            UUID.fromString(rs.getString("id")),
                            UUID.fromString(rs.getString("user_id")),
                            rs.getDate("day").toLocalDate(),
                            UUID.fromString(rs.getString("req_id")),
                            rs.getLong("frozen_tokens"),
                            rs.getString("status"),
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getTimestamp("updated_at").toInstant()
                    )
            ).map(Optional::of).orElse(Optional.empty());
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Transactional
    public Optional<UUID> tryFreezeTokens(UUID userId, LocalDate day, long tokensToFreeze, long dailyLimit) {
        if (tokensToFreeze <= 0) {
            return Optional.empty();
        }
        if (dailyLimit > 0 && tokensToFreeze > dailyLimit) {
            log.warn("Cannot freeze {} tokens for user {} on {}: exceeds daily limit {}",
                    tokensToFreeze, userId, day, dailyLimit);
            return Optional.empty();
        }

        UUID reqId = UUID.randomUUID();

        int inserted = jdbcTemplate.update("""
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
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("day", day)
                .addValue("reqId", reqId)
                .addValue("tokensToFreeze", tokensToFreeze)
                .addValue("dailyLimit", dailyLimit));

        if (inserted <= 0) {
            log.warn("Failed to freeze {} tokens for user {} on {} (dailyLimit={}): concurrent quota check failed",
                    tokensToFreeze, userId, day, dailyLimit);
            return Optional.empty();
        }

        int updated = jdbcTemplate.update("""
                INSERT INTO assistant.assistant_chat_token_usage_daily (
                    user_id, day, used_tokens, frozen_tokens, last_frozen_at, updated_at
                ) VALUES (
                    :userId, :day, 0, :tokensToFreeze, NOW(), NOW()
                )
                ON CONFLICT (user_id, day) DO UPDATE SET
                    frozen_tokens = assistant.assistant_chat_token_usage_daily.frozen_tokens + :tokensToFreeze,
                    last_frozen_at = NOW(),
                    updated_at = NOW()
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("day", day)
                .addValue("tokensToFreeze", tokensToFreeze));

        if (updated <= 0) {
            log.error("Failed to update summary table after freezing {} tokens for user {} on {}",
                    tokensToFreeze, userId, day);
        }

        log.debug("Successfully frozen {} tokens for user {} on {}, reqId={}",
                tokensToFreeze, userId, day, reqId);
        return Optional.of(reqId);
    }

    @Transactional
    public void settleAndRelease(UUID reqId, long actualTokensUsed) {
        Optional<FreezeRecord> recordOpt = findFreezeRecordByReqId(reqId);
        if (recordOpt.isEmpty()) {
            log.warn("Cannot settle: freeze record not found for reqId={}", reqId);
            return;
        }

        FreezeRecord record = recordOpt.get();
        if (!"ACTIVE".equals(record.status())) {
            log.warn("Cannot settle: freeze record {} has status '{}', expected 'ACTIVE'",
                    reqId, record.status());
            return;
        }

        int updated = jdbcTemplate.update("""
                UPDATE assistant.assistant_chat_token_freeze
                SET status = 'SETTLED', updated_at = NOW()
                WHERE req_id = :reqId AND status = 'ACTIVE'
                """, new MapSqlParameterSource().addValue("reqId", reqId));

        if (updated <= 0) {
            log.warn("Failed to update freeze record status for reqId={}", reqId);
            return;
        }

        if (actualTokensUsed > 0) {
            jdbcTemplate.update("""
                    INSERT INTO assistant.assistant_chat_token_usage_daily (
                        user_id, day, used_tokens, frozen_tokens, updated_at
                    ) VALUES (
                        :userId, :day, :actualUsed, :frozenToRelease * -1, NOW()
                    )
                    ON CONFLICT (user_id, day) DO UPDATE SET
                        used_tokens = assistant.assistant_chat_token_usage_daily.used_tokens + :actualUsed,
                        frozen_tokens = GREATEST(0, assistant.assistant_chat_token_usage_daily.frozen_tokens - :frozenToRelease),
                        updated_at = NOW()
                    """, new MapSqlParameterSource()
                    .addValue("userId", record.userId())
                    .addValue("day", record.day())
                    .addValue("actualUsed", actualTokensUsed)
                    .addValue("frozenToRelease", record.frozenTokens()));
        } else {
            jdbcTemplate.update("""
                    UPDATE assistant.assistant_chat_token_usage_daily
                    SET frozen_tokens = GREATEST(0, frozen_tokens - :frozenToRelease),
                        updated_at = NOW()
                    WHERE user_id = :userId AND day = :day
                    """, new MapSqlParameterSource()
                    .addValue("userId", record.userId())
                    .addValue("day", record.day())
                    .addValue("frozenToRelease", record.frozenTokens()));
        }

        log.debug("Settled freeze record {}: actualUsed={}, frozenReleased={}",
                reqId, actualTokensUsed, record.frozenTokens());
    }

    @Transactional
    public void releaseFrozenTokens(UUID reqId) {
        Optional<FreezeRecord> recordOpt = findFreezeRecordByReqId(reqId);
        if (recordOpt.isEmpty()) {
            log.warn("Cannot release: freeze record not found for reqId={}", reqId);
            return;
        }

        FreezeRecord record = recordOpt.get();
        if (!"ACTIVE".equals(record.status())) {
            log.warn("Cannot release: freeze record {} has status '{}', expected 'ACTIVE'",
                    reqId, record.status());
            return;
        }

        int updated = jdbcTemplate.update("""
                UPDATE assistant.assistant_chat_token_freeze
                SET status = 'RELEASED', updated_at = NOW()
                WHERE req_id = :reqId AND status = 'ACTIVE'
                """, new MapSqlParameterSource().addValue("reqId", reqId));

        if (updated <= 0) {
            log.warn("Failed to update freeze record status for reqId={}", reqId);
            return;
        }

        jdbcTemplate.update("""
                UPDATE assistant.assistant_chat_token_usage_daily
                SET frozen_tokens = GREATEST(0, frozen_tokens - :frozenToRelease),
                    updated_at = NOW()
                WHERE user_id = :userId AND day = :day
                """, new MapSqlParameterSource()
                .addValue("userId", record.userId())
                .addValue("day", record.day())
                .addValue("frozenToRelease", record.frozenTokens()));

        log.info("Released frozen tokens for reqId={}, user={}, day={}, tokens={}",
                reqId, record.userId(), record.day(), record.frozenTokens());
    }

    @Transactional
    public int cleanupExpiredFrozenTokens(Duration maxAge) {
        var params = new MapSqlParameterSource()
                .addValue("maxAgeSeconds", maxAge.toSeconds())
                .addValue("cutoff", java.time.Instant.now().minus(maxAge));

        int updatedFreeze = jdbcTemplate.update("""
                UPDATE assistant.assistant_chat_token_freeze
                SET status = 'RELEASED', updated_at = NOW()
                WHERE status = 'ACTIVE'
                  AND created_at < :cutoff
                """, params);

        if (updatedFreeze > 0) {
            log.warn("Marked {} expired freeze records as RELEASED (age > {})", updatedFreeze, maxAge);

            int updatedSummary = jdbcTemplate.update("""
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
                    """, new MapSqlParameterSource());

            if (updatedSummary > 0) {
                log.debug("Updated frozen_tokens summary for {} users", updatedSummary);
            }
        }

        return updatedFreeze;
    }

    @Deprecated
    public boolean tryFreezeTokens(UUID userId, LocalDate day, long tokensToFreeze) {
        throw new UnsupportedOperationException("Use tryFreezeTokens with reqId and dailyLimit instead");
    }

    @Deprecated
    public void settleAndRelease(UUID userId, LocalDate day, long actualTokensUsed, long frozenTokensToRelease) {
        throw new UnsupportedOperationException("Use settleAndRelease with reqId instead");
    }

    @Deprecated
    public void releaseFrozenTokens(UUID userId, LocalDate day, long tokensToRelease) {
        throw new UnsupportedOperationException("Use releaseFrozenTokens with reqId instead");
    }

    @Deprecated
    public long getFrozenTokens(UUID userId, LocalDate day) {
        return getActiveFrozenTokens(userId, day);
    }

    @Deprecated
    public long addTokens(UUID userId, LocalDate day, long deltaTokens) {
        if (deltaTokens <= 0) {
            return getUsedTokens(userId, day);
        }
        jdbcTemplate.update("""
                INSERT INTO assistant.assistant_chat_token_usage_daily (
                  user_id,
                  day,
                  used_tokens,
                  updated_at
                ) VALUES (
                  :userId,
                  :day,
                  :delta,
                  NOW()
                )
                ON CONFLICT (user_id, day) DO UPDATE SET
                  used_tokens = assistant.assistant_chat_token_usage_daily.used_tokens + EXCLUDED.used_tokens,
                  updated_at = NOW()
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("day", day)
                .addValue("delta", deltaTokens));

        return getUsedTokens(userId, day);
    }
}
