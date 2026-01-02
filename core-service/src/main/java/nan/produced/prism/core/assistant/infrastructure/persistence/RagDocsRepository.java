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
    private volatile String distanceExprTemplate = "c.embedding <=> CAST(:queryVector AS %s)";

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
        // Resolve pgvector's real type location once. We do NOT trust schema-qualified names like
        // assistant.vector because a conflicting user-defined type can exist in that schema.
        String typeSql = """
                SELECT
                  format('%I', n.nspname) AS schema_sql,
                  format('%I.%I', n.nspname, t.typname) AS type_sql,
                  t.oid AS type_oid
                FROM pg_extension e
                JOIN pg_depend dep
                  ON dep.refobjid = e.oid
                 AND dep.deptype = 'e'
                 AND dep.classid = 'pg_type'::regclass
                JOIN pg_type t ON t.oid = dep.objid
                JOIN pg_namespace n ON n.oid = t.typnamespace
                WHERE e.extname = 'vector'
                  AND t.typname = 'vector'
                LIMIT 1
                """;

        PgVectorTypeInfo typeInfo = jdbcTemplate.query(typeSql, rs -> {
            if (!rs.next()) {
                return null;
            }
            return new PgVectorTypeInfo(
                    rs.getString("schema_sql"),
                    rs.getString("type_sql"),
                    rs.getLong("type_oid")
            );
        });

        if (typeInfo == null || typeInfo.typeSql == null || typeInfo.typeSql.isBlank()) {
            throw new IllegalStateException("pgvector extension type not found (expected extension 'vector' installed)");
        }
        this.vectorTypeForCast = typeInfo.typeSql;

        // Determine which distance expression really works at runtime.
        // Catalog checks alone are not always reliable (e.g. type shadowing / old extension installs).
        DistanceExprCandidate[] candidates = new DistanceExprCandidate[] {
                // cosine (preferred, matches vector_cosine_ops)
                new DistanceExprCandidate(
                        "CAST(c.embedding AS %s) <=> CAST(:queryVector AS %s)",
                        "CAST(:v1 AS %s) <=> CAST(:v2 AS %s)",
                        "<=>",
                        null
                ),
                new DistanceExprCandidate(
                        typeInfo.schemaSql + ".cosine_distance(CAST(c.embedding AS %s), CAST(:queryVector AS %s))",
                        typeInfo.schemaSql + ".cosine_distance(CAST(:v1 AS %s), CAST(:v2 AS %s))",
                        null,
                        "cosine_distance"
                ),
                // L2 fallback (works on older pgvector versions)
                new DistanceExprCandidate(
                        "CAST(c.embedding AS %s) <-> CAST(:queryVector AS %s)",
                        "CAST(:v1 AS %s) <-> CAST(:v2 AS %s)",
                        "<->",
                        null
                ),
                new DistanceExprCandidate(
                        typeInfo.schemaSql + ".l2_distance(CAST(c.embedding AS %s), CAST(:queryVector AS %s))",
                        typeInfo.schemaSql + ".l2_distance(CAST(:v1 AS %s), CAST(:v2 AS %s))",
                        null,
                        "l2_distance"
                )
        };

        for (DistanceExprCandidate candidate : candidates) {
            if (candidate.requiredOperator != null && !hasOperator(candidate.requiredOperator, typeInfo.typeOid)) {
                continue;
            }
            if (candidate.requiredFunction != null && !hasDistanceFunction(candidate.requiredFunction, typeInfo.typeOid)) {
                continue;
            }
            if (probeDistanceExpr(candidate.probeExprTemplate, vectorTypeForCast)) {
                this.distanceExprTemplate = candidate.distanceExprTemplate;
                return;
            }
        }

        throw new IllegalStateException("pgvector distance operator/function not found or not executable (expected <=>/cosine_distance/<->/l2_distance)");
    }

    private static final class DistanceExprCandidate {
        private final String distanceExprTemplate;
        private final String probeExprTemplate;
        private final String requiredOperator;
        private final String requiredFunction;

        private DistanceExprCandidate(String distanceExprTemplate,
                                      String probeExprTemplate,
                                      String requiredOperator,
                                      String requiredFunction) {
            this.distanceExprTemplate = distanceExprTemplate;
            this.probeExprTemplate = probeExprTemplate;
            this.requiredOperator = requiredOperator;
            this.requiredFunction = requiredFunction;
        }
    }

    private boolean probeDistanceExpr(String probeExprTemplate, String vectorTypeSql) {
        if (probeExprTemplate == null || probeExprTemplate.isBlank()) {
            return false;
        }
        String expr = probeExprTemplate.formatted(vectorTypeSql, vectorTypeSql);
        String sql = "SELECT (" + expr + ") AS distance";
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("v1", "[0,0,0]")
                .addValue("v2", "[0,0,0]");
        try {
            jdbcTemplate.queryForObject(sql, p, Double.class);
            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    private boolean hasOperator(String name, long vectorTypeOid) {
        String sql = """
                SELECT EXISTS (
                  SELECT 1
                  FROM pg_operator o
                  WHERE o.oprname = :name
                    AND o.oprleft = :typeOid::oid
                    AND o.oprright = :typeOid::oid
                )
                """;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("name", name)
                .addValue("typeOid", vectorTypeOid);
        Boolean exists = jdbcTemplate.queryForObject(sql, p, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    private boolean hasDistanceFunction(String name, long vectorTypeOid) {
        String sql = """
                SELECT EXISTS (
                  SELECT 1
                  FROM pg_proc p
                  WHERE p.proname = :name
                    AND p.pronargs = 2
                    AND p.proargtypes[0] = :typeOid::oid
                    AND p.proargtypes[1] = :typeOid::oid
                )
                """;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("name", name)
                .addValue("typeOid", vectorTypeOid);
        Boolean exists = jdbcTemplate.queryForObject(sql, p, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    private static final class PgVectorTypeInfo {
        private final String schemaSql;
        private final String typeSql;
        private final long typeOid;

        private PgVectorTypeInfo(String schemaSql, String typeSql, long typeOid) {
            this.schemaSql = schemaSql;
            this.typeSql = typeSql;
            this.typeOid = typeOid;
        }
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

        String distanceExpr = distanceExprTemplate.formatted(vectorTypeForCast, vectorTypeForCast);
        String sql = """
                SELECT
                  d.doc_key,
                  d.lang,
                  d.slug,
                  d.title,
                  c.heading_path,
                  c.chunk_text,
                  (%s) AS distance
                FROM assistant.rag_chunk c
                JOIN assistant.rag_document d ON d.id = c.doc_id
                WHERE d.lang = :lang
                  AND d.audience = 'user'
                  AND d.status = 'stable'
                ORDER BY %s
                LIMIT :topK
                """.formatted(distanceExpr, distanceExpr);

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
