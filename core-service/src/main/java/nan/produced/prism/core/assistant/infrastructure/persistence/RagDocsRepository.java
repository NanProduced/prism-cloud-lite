package nan.produced.prism.core.assistant.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.Objects;

@Repository
@RequiredArgsConstructor
public class RagDocsRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private volatile String vectorTypeForCast = "vector";

    public record UpsertDocumentParams(
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
            String docHash
    ) {
    }

    public UUID upsertDocument(UpsertDocumentParams params) {
        String sql = """
                INSERT INTO assistant.rag_document (
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
                  NOW(),
                  NOW()
                )
                ON CONFLICT (doc_key, lang) DO UPDATE SET
                  slug = EXCLUDED.slug,
                  title = EXCLUDED.title,
                  module = EXCLUDED.module,
                  audience = EXCLUDED.audience,
                  status = EXCLUDED.status,
                  owner = EXCLUDED.owner,
                  last_updated = EXCLUDED.last_updated,
                  source_path = EXCLUDED.source_path,
                  doc_version = EXCLUDED.doc_version,
                  doc_hash = EXCLUDED.doc_hash,
                  updated_at = NOW()
                RETURNING id
                """;

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", params.id())
                .addValue("docKey", params.docKey())
                .addValue("lang", params.lang())
                .addValue("slug", params.slug())
                .addValue("title", params.title())
                .addValue("module", params.module())
                .addValue("audience", params.audience())
                .addValue("status", params.status())
                .addValue("owner", params.owner())
                .addValue("lastUpdated", params.lastUpdated())
                .addValue("sourcePath", params.sourcePath())
                .addValue("docVersion", params.docVersion())
                .addValue("docHash", params.docHash());

        return jdbcTemplate.queryForObject(sql, p, (rs, rowNum) -> rs.getObject("id", UUID.class));
    }

    public void replaceChunks(UUID docId, List<ChunkRow> chunks) {
        jdbcTemplate.update("DELETE FROM assistant.rag_chunk WHERE doc_id = :docId", new MapSqlParameterSource("docId", docId));
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO assistant.rag_chunk (
                  doc_id,
                  chunk_index,
                  heading_path,
                  chunk_text,
                  chunk_hash,
                  char_count,
                  embedding,
                  created_at
                )
                VALUES (
                  :docId,
                  :chunkIndex,
                  :headingPath,
                  :chunkText,
                  :chunkHash,
                  :charCount,
                  CAST(:embedding AS %s),
                  NOW()
                )
                """.formatted(vectorTypeForCast);

        MapSqlParameterSource[] batch = new MapSqlParameterSource[chunks.size()];
        for (int i = 0; i < chunks.size(); i++) {
            var c = chunks.get(i);
            batch[i] = new MapSqlParameterSource()
                    .addValue("docId", docId)
                    .addValue("chunkIndex", c.chunkIndex())
                    .addValue("headingPath", c.headingPath())
                    .addValue("chunkText", c.chunkText())
                    .addValue("chunkHash", c.chunkHash())
                    .addValue("charCount", c.charCount())
                    .addValue("embedding", c.embeddingVectorLiteral());
        }

        jdbcTemplate.batchUpdate(sql, batch);
    }

    @PostConstruct
    void resolveVectorType() {
        // pgvector can be installed into different schemas; resolve the actual type name once.
        String sql = """
                SELECT
                  CASE
                    WHEN to_regtype('assistant.vector') IS NOT NULL THEN 'assistant.vector'
                    WHEN to_regtype('public.vector') IS NOT NULL THEN 'public.vector'
                    WHEN to_regtype('vector') IS NOT NULL THEN 'vector'
                    ELSE NULL
                  END AS vector_type
                """;
        String resolved = jdbcTemplate.getJdbcTemplate().queryForObject(sql, String.class);
        if (resolved == null || resolved.isBlank()) {
            throw new IllegalStateException("pgvector type not found (expected assistant.vector/public.vector/vector)");
        }
        this.vectorTypeForCast = resolved;
    }

    public record RagChunkHit(
            String docKey,
            String lang,
            String slug,
            String title,
            String headingPath,
            String chunkText,
            double distance
    ) {
    }

    public List<RagChunkHit> searchTopChunks(String queryVectorLiteral, String lang, int topK) {
        Objects.requireNonNull(queryVectorLiteral, "queryVectorLiteral");
        if (lang == null || lang.isBlank() || topK <= 0) {
            return List.of();
        }

        String sql = """
                SELECT
                  d.doc_key,
                  d.lang,
                  d.slug,
                  d.title,
                  c.heading_path,
                  c.chunk_text,
                  (c.embedding <=> CAST(:queryVector AS %s)) AS distance
                FROM assistant.rag_chunk c
                JOIN assistant.rag_document d ON d.id = c.doc_id
                WHERE d.lang = :lang
                  AND d.audience = 'user'
                  AND d.status = 'stable'
                ORDER BY c.embedding <=> CAST(:queryVector AS %s)
                LIMIT :topK
                """.formatted(vectorTypeForCast, vectorTypeForCast);

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("queryVector", queryVectorLiteral)
                .addValue("lang", lang)
                .addValue("topK", topK);

        return jdbcTemplate.query(sql, p, (rs, rowNum) -> new RagChunkHit(
                rs.getString("doc_key"),
                rs.getString("lang"),
                rs.getString("slug"),
                rs.getString("title"),
                rs.getString("heading_path"),
                rs.getString("chunk_text"),
                rs.getDouble("distance")
        ));
    }

    public record ChunkRow(
            int chunkIndex,
            String headingPath,
            String chunkText,
            String chunkHash,
            int charCount,
            String embeddingVectorLiteral
    ) {
    }
}
