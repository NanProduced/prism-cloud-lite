package nan.produced.prism.core.assistant.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AssistantRagParentRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public record ParentRow(
            UUID id,
            String docKey,
            String lang,
            String slug,
            String title,
            String module,
            String audience,
            String status,
            String owner,
            LocalDate lastUpdated,
            String sourcePath,
            String docVersion,
            String docHash,
            String headingPath,
            int parentIndex,
            String parentText,
            int charCount
    ) {
    }

    public record ParentDoc(
            UUID id,
            String docKey,
            String lang,
            String slug,
            String title,
            String headingPath,
            int parentIndex,
            String parentText
    ) {
    }

    public record ParentHit(
            UUID id,
            String docKey,
            String lang,
            String slug,
            String title,
            String headingPath,
            int parentIndex,
            String parentText,
            double rank
    ) {
    }

    public void replaceParents(String docKey, String lang, List<ParentRow> rows) {
        if (!StringUtils.hasText(docKey) || !StringUtils.hasText(lang)) {
            return;
        }

        jdbcTemplate.update(
                "DELETE FROM assistant.rag_parent WHERE doc_key = :docKey AND lang = :lang",
                new MapSqlParameterSource()
                        .addValue("docKey", docKey)
                        .addValue("lang", lang)
        );

        if (rows == null || rows.isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO assistant.rag_parent (
                  id,
                  doc_key,
                  lang,
                  slug,
                  title,
                  module,
                  audience,
                  status,
                  owner,
                  last_updated,
                  source_path,
                  doc_version,
                  doc_hash,
                  heading_path,
                  parent_index,
                  parent_text,
                  char_count,
                  created_at,
                  updated_at
                )
                VALUES (
                  :id,
                  :docKey,
                  :lang,
                  :slug,
                  :title,
                  :module,
                  :audience,
                  :status,
                  :owner,
                  :lastUpdated,
                  :sourcePath,
                  :docVersion,
                  :docHash,
                  :headingPath,
                  :parentIndex,
                  :parentText,
                  :charCount,
                  NOW(),
                  NOW()
                )
                """;

        MapSqlParameterSource[] batch = new MapSqlParameterSource[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            ParentRow row = rows.get(i);
            batch[i] = new MapSqlParameterSource()
                    .addValue("id", row.id())
                    .addValue("docKey", row.docKey())
                    .addValue("lang", row.lang())
                    .addValue("slug", row.slug())
                    .addValue("title", row.title())
                    .addValue("module", row.module())
                    .addValue("audience", row.audience())
                    .addValue("status", row.status())
                    .addValue("owner", row.owner())
                    .addValue("lastUpdated", row.lastUpdated())
                    .addValue("sourcePath", row.sourcePath())
                    .addValue("docVersion", row.docVersion())
                    .addValue("docHash", row.docHash())
                    .addValue("headingPath", row.headingPath())
                    .addValue("parentIndex", row.parentIndex())
                    .addValue("parentText", row.parentText())
                    .addValue("charCount", row.charCount());
        }

        jdbcTemplate.batchUpdate(sql, batch);
    }

    public List<ParentDoc> findByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        String sql = """
                SELECT
                  id,
                  doc_key,
                  lang,
                  slug,
                  title,
                  heading_path,
                  parent_index,
                  parent_text
                FROM assistant.rag_parent
                WHERE id IN (:ids)
                """;

        return jdbcTemplate.query(sql, new MapSqlParameterSource("ids", ids), (rs, rowNum) -> new ParentDoc(
                rs.getObject("id", UUID.class),
                rs.getString("doc_key"),
                rs.getString("lang"),
                rs.getString("slug"),
                rs.getString("title"),
                rs.getString("heading_path"),
                rs.getInt("parent_index"),
                rs.getString("parent_text")
        ));
    }

    public List<ParentHit> searchByFullText(String query, String lang, String audience, String status, int topK) {
        if (!StringUtils.hasText(query) || !StringUtils.hasText(lang) || topK <= 0) {
            return List.of();
        }

        String ftsColumn = "zh".equalsIgnoreCase(lang) ? "fts_zh" : "fts_en";
        String config = "zh".equalsIgnoreCase(lang) ? "jieba_cfg" : "english";

        String sql = """
                SELECT
                  id,
                  doc_key,
                  lang,
                  slug,
                  title,
                  heading_path,
                  parent_index,
                  parent_text,
                  ts_rank_cd(%s, websearch_to_tsquery('%s', :query)) AS rank
                FROM assistant.rag_parent
                WHERE lang = :lang
                  AND audience = :audience
                  AND status = :status
                  AND %s @@ websearch_to_tsquery('%s', :query)
                ORDER BY rank DESC
                LIMIT :topK
                """.formatted(ftsColumn, config, ftsColumn, config);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("query", query)
                .addValue("lang", lang)
                .addValue("audience", StringUtils.hasText(audience) ? audience : "user")
                .addValue("status", StringUtils.hasText(status) ? status : "stable")
                .addValue("topK", topK);

        List<ParentHit> hits = jdbcTemplate.query(sql, params, (rs, rowNum) -> new ParentHit(
                rs.getObject("id", UUID.class),
                rs.getString("doc_key"),
                rs.getString("lang"),
                rs.getString("slug"),
                rs.getString("title"),
                rs.getString("heading_path"),
                rs.getInt("parent_index"),
                rs.getString("parent_text"),
                rs.getDouble("rank")
        ));
        return hits != null ? hits : List.of();
    }
}
