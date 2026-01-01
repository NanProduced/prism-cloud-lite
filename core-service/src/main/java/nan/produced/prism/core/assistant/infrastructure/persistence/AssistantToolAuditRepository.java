package nan.produced.prism.core.assistant.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AssistantToolAuditRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void insert(UUID id,
                       UUID userId,
                       String toolCallId,
                       String toolName,
                       JsonNode inputJson,
                       boolean success,
                       long elapsedMs,
                       String errorMessage) {
        jdbcTemplate.update("""
                INSERT INTO assistant.assistant_tool_audit_log (
                  id,
                  user_id,
                  tool_call_id,
                  tool_name,
                  input_json,
                  success,
                  elapsed_ms,
                  error_message,
                  created_at
                ) VALUES (
                  :id,
                  :userId,
                  :toolCallId,
                  :toolName,
                  CAST(:inputJson AS jsonb),
                  :success,
                  :elapsedMs,
                  :errorMessage,
                  NOW()
                )
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("userId", userId)
                .addValue("toolCallId", toolCallId)
                .addValue("toolName", toolName)
                .addValue("inputJson", inputJson != null ? inputJson.toString() : null)
                .addValue("success", success)
                .addValue("elapsedMs", elapsedMs)
                .addValue("errorMessage", errorMessage));
    }
}

