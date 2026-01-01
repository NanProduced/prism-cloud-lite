package nan.produced.prism.core.assistant.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserAiModelConfigRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public record UserAiModelConfigRow(
            UUID id,
            UUID userId,
            String provider,
            String model,
            boolean enabled,
            boolean isDefault,
            String apiKeyCiphertext,
            String apiKeyLast4,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public List<UserAiModelConfigRow> listByUser(UUID userId) {
        String sql = """
                SELECT
                  id,
                  user_id,
                  provider,
                  model,
                  enabled,
                  is_default,
                  api_key_ciphertext,
                  api_key_last4,
                  created_at,
                  updated_at
                FROM assistant.user_ai_model_config
                WHERE user_id = :userId
                ORDER BY is_default DESC, provider ASC
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("userId", userId), (rs, rowNum) -> new UserAiModelConfigRow(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getBoolean("enabled"),
                rs.getBoolean("is_default"),
                rs.getString("api_key_ciphertext"),
                rs.getString("api_key_last4"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        ));
    }

    public UserAiModelConfigRow getByUserAndProvider(UUID userId, String provider) {
        String sql = """
                SELECT
                  id,
                  user_id,
                  provider,
                  model,
                  enabled,
                  is_default,
                  api_key_ciphertext,
                  api_key_last4,
                  created_at,
                  updated_at
                FROM assistant.user_ai_model_config
                WHERE user_id = :userId
                  AND provider = :provider
                """;
        var rows = jdbcTemplate.query(sql, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("provider", provider), (rs, rowNum) -> new UserAiModelConfigRow(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getBoolean("enabled"),
                rs.getBoolean("is_default"),
                rs.getString("api_key_ciphertext"),
                rs.getString("api_key_last4"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        ));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public UserAiModelConfigRow getDefaultByUser(UUID userId) {
        String sql = """
                SELECT
                  id,
                  user_id,
                  provider,
                  model,
                  enabled,
                  is_default,
                  api_key_ciphertext,
                  api_key_last4,
                  created_at,
                  updated_at
                FROM assistant.user_ai_model_config
                WHERE user_id = :userId
                  AND is_default = TRUE
                LIMIT 1
                """;
        var rows = jdbcTemplate.query(sql, new MapSqlParameterSource("userId", userId), (rs, rowNum) -> new UserAiModelConfigRow(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getBoolean("enabled"),
                rs.getBoolean("is_default"),
                rs.getString("api_key_ciphertext"),
                rs.getString("api_key_last4"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        ));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void upsert(UUID id,
                       UUID userId,
                       String provider,
                       String model,
                       boolean enabled,
                       boolean isDefault,
                       String apiKeyCiphertext,
                       String apiKeyLast4) {
        String sql = """
                INSERT INTO assistant.user_ai_model_config (
                  id,
                  user_id,
                  provider,
                  model,
                  enabled,
                  is_default,
                  api_key_ciphertext,
                  api_key_last4,
                  created_at,
                  updated_at
                )
                VALUES (
                  :id,
                  :userId,
                  :provider,
                  :model,
                  :enabled,
                  :isDefault,
                  :apiKeyCiphertext,
                  :apiKeyLast4,
                  NOW(),
                  NOW()
                )
                ON CONFLICT (user_id, provider) DO UPDATE SET
                  model = EXCLUDED.model,
                  enabled = EXCLUDED.enabled,
                  is_default = EXCLUDED.is_default,
                  api_key_ciphertext = EXCLUDED.api_key_ciphertext,
                  api_key_last4 = EXCLUDED.api_key_last4,
                  updated_at = NOW()
                """;
        jdbcTemplate.update(sql, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("userId", userId)
                .addValue("provider", provider)
                .addValue("model", model)
                .addValue("enabled", enabled)
                .addValue("isDefault", isDefault)
                .addValue("apiKeyCiphertext", apiKeyCiphertext)
                .addValue("apiKeyLast4", apiKeyLast4));
    }

    public void clearDefaultForUser(UUID userId) {
        jdbcTemplate.update("""
                UPDATE assistant.user_ai_model_config
                SET is_default = FALSE, updated_at = NOW()
                WHERE user_id = :userId
                """, new MapSqlParameterSource("userId", userId));
    }

    public void deleteByUserAndProvider(UUID userId, String provider) {
        jdbcTemplate.update("""
                DELETE FROM assistant.user_ai_model_config
                WHERE user_id = :userId
                  AND provider = :provider
                """, new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("provider", provider));
    }
}
