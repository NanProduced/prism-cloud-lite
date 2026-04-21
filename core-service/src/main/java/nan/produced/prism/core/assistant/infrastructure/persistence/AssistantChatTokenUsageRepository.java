package nan.produced.prism.core.assistant.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.dao.EmptyResultDataAccessException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AssistantChatTokenUsageRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

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

    public long getFrozenTokens(UUID userId, LocalDate day) {
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

    public long getTotalCommittedAndFrozen(UUID userId, LocalDate day) {
        try {
            var result = jdbcTemplate.getJdbcTemplate().queryForObject(
                    "SELECT used_tokens + frozen_tokens FROM assistant.assistant_chat_token_usage_daily WHERE user_id = ? AND day = ?",
                    Long.class,
                    userId,
                    day
            );
            return result != null ? result : 0L;
        } catch (EmptyResultDataAccessException ignored) {
            return 0L;
        }
    }

    public boolean tryFreezeTokens(UUID userId, LocalDate day, long tokensToFreeze, long dailyLimit) {
        if (tokensToFreeze <= 0) {
            return true;
        }
        if (dailyLimit > 0 && tokensToFreeze > dailyLimit) {
            return false;
        }
        int updated = jdbcTemplate.update("""
                INSERT INTO assistant.assistant_chat_token_usage_daily (
                  user_id,
                  day,
                  used_tokens,
                  frozen_tokens,
                  last_frozen_at,
                  updated_at
                )
                SELECT
                  :userId,
                  :day,
                  0,
                  :tokensToFreeze,
                  NOW(),
                  NOW()
                WHERE :dailyLimit <= 0 OR :tokensToFreeze <= :dailyLimit
                ON CONFLICT (user_id, day) DO UPDATE SET
                  frozen_tokens = assistant.assistant_chat_token_usage_daily.frozen_tokens + :tokensToFreeze,
                  last_frozen_at = NOW(),
                  updated_at = NOW()
                WHERE :dailyLimit <= 0
                   OR (assistant.assistant_chat_token_usage_daily.used_tokens
                       + assistant.assistant_chat_token_usage_daily.frozen_tokens
                       + :tokensToFreeze) <= :dailyLimit
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("day", day)
                .addValue("tokensToFreeze", tokensToFreeze)
                .addValue("dailyLimit", dailyLimit));
        return updated > 0;
    }

    public void settleAndRelease(UUID userId, LocalDate day, long actualTokensUsed, long frozenTokensToRelease) {
        if (frozenTokensToRelease <= 0 && actualTokensUsed <= 0) {
            return;
        }
        if (actualTokensUsed > 0) {
            jdbcTemplate.update("""
                    INSERT INTO assistant.assistant_chat_token_usage_daily (
                      user_id,
                      day,
                      used_tokens,
                      frozen_tokens,
                      updated_at
                    ) VALUES (
                      :userId,
                      :day,
                      :actualUsed,
                      0,
                      NOW()
                    )
                    ON CONFLICT (user_id, day) DO UPDATE SET
                      used_tokens = assistant.assistant_chat_token_usage_daily.used_tokens + :actualUsed,
                      frozen_tokens = GREATEST(0, assistant.assistant_chat_token_usage_daily.frozen_tokens - :frozenToRelease),
                      updated_at = NOW()
                    """, new MapSqlParameterSource()
                    .addValue("userId", userId)
                    .addValue("day", day)
                    .addValue("actualUsed", actualTokensUsed)
                    .addValue("frozenToRelease", frozenTokensToRelease));
        } else {
            jdbcTemplate.update("""
                    UPDATE assistant.assistant_chat_token_usage_daily
                    SET frozen_tokens = GREATEST(0, frozen_tokens - :frozenToRelease),
                        updated_at = NOW()
                    WHERE user_id = :userId AND day = :day
                    """, new MapSqlParameterSource()
                    .addValue("userId", userId)
                    .addValue("day", day)
                    .addValue("frozenToRelease", frozenTokensToRelease));
        }
    }

    public void releaseFrozenTokens(UUID userId, LocalDate day, long tokensToRelease) {
        if (tokensToRelease <= 0) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE assistant.assistant_chat_token_usage_daily
                SET frozen_tokens = GREATEST(0, frozen_tokens - :tokensToRelease),
                    updated_at = NOW()
                WHERE user_id = :userId AND day = :day
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("day", day)
                .addValue("tokensToRelease", tokensToRelease));
    }

    public int cleanupExpiredFrozenTokens(Duration maxAge) {
        return jdbcTemplate.update("""
                UPDATE assistant.assistant_chat_token_usage_daily
                SET frozen_tokens = 0,
                    last_frozen_at = NULL,
                    updated_at = NOW()
                WHERE frozen_tokens > 0
                  AND last_frozen_at IS NOT NULL
                  AND last_frozen_at < NOW() - :maxAgeSeconds * INTERVAL '1 second'
                """, new MapSqlParameterSource()
                .addValue("maxAgeSeconds", maxAge.toSeconds()));
    }

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
