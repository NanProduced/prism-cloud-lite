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
                       String traceId,
                       String inputSummary,
                       String outputSummary,
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
                  trace_id,
                  input_summary,
                  output_summary,
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
                  :traceId,
                  :inputSummary,
                  :outputSummary,
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
                .addValue("traceId", traceId)
                .addValue("inputSummary", inputSummary)
                .addValue("outputSummary", outputSummary)
                .addValue("success", success)
                .addValue("elapsedMs", elapsedMs)
                .addValue("errorMessage", errorMessage));
    }
}
