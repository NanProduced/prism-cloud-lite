package nan.produced.prism.core.assistant.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.dao.EmptyResultDataAccessException;

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
