package nan.produced.prism.core.assistant.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AssistantChatTokenAuditRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void insert(UUID id,
                        UUID userId,
                        String traceId,
                        Integer promptTokens,
                        Integer completionTokens,
                        Integer totalTokens,
                        String model,
                        String provider,
                        String inputSummary,
                        String outputSummary) {
        jdbcTemplate.update("""
                INSERT INTO assistant.assistant_chat_token_audit_log (
                  id,
                  user_id,
                  trace_id,
                  prompt_tokens,
                  completion_tokens,
                  total_tokens,
                  model,
                  provider,
                  input_summary,
                  output_summary,
                  created_at
                ) VALUES (
                  :id,
                  :userId,
                  :traceId,
                  :promptTokens,
                  :completionTokens,
                  :totalTokens,
                  :model,
                  :provider,
                  :inputSummary,
                  :outputSummary,
                  NOW()
                )
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("userId", userId)
                .addValue("traceId", traceId)
                .addValue("promptTokens", promptTokens)
                .addValue("completionTokens", completionTokens)
                .addValue("totalTokens", totalTokens)
                .addValue("model", model)
                .addValue("provider", provider)
                .addValue("inputSummary", inputSummary)
                .addValue("outputSummary", outputSummary));
    }
}
