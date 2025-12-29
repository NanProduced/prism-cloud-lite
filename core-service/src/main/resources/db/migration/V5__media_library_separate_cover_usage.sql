-- Separate Media Library cover images into their own billing bucket (StorageFileType=COVER).
--
-- Before: cover images were counted as IMAGE because they are image/* files.
-- After: cover images are counted as COVER, and the IMAGE bucket excludes cover images.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

WITH cover_distinct AS (
    SELECT DISTINCT
        a.user_id AS user_id,
        f.file_id AS file_id,
        COALESCE(f.size, 0) AS size_bytes
    FROM pcc_media_asset a
    JOIN pcc_file_entity f
      ON f.file_id = a.cover_file_id
    WHERE a.cover_file_id IS NOT NULL
      AND f.mime_type ILIKE 'image/%'
),
cover_by_user AS (
    SELECT
        user_id,
        COUNT(*)::BIGINT AS cover_count,
        COALESCE(SUM(size_bytes), 0)::BIGINT AS cover_bytes
    FROM cover_distinct
    GROUP BY user_id
)
INSERT INTO pcc_user_storage_usage (id, user_id, source_type, file_type, file_count, total_bytes, updated_at)
SELECT
    gen_random_uuid(),
    user_id,
    'MEDIA_LIBRARY',
    'COVER',
    cover_count::INT,
    cover_bytes,
    NOW()
FROM cover_by_user
ON CONFLICT (user_id, source_type, file_type)
DO UPDATE SET
    file_count = EXCLUDED.file_count,
    total_bytes = EXCLUDED.total_bytes,
    updated_at = NOW();

-- Move cover bytes/count out of IMAGE bucket (best-effort, keep non-negative).
UPDATE pcc_user_storage_usage u
SET
    file_count = GREATEST(0, u.file_count - c.cover_count::INT),
    total_bytes = GREATEST(0, u.total_bytes - c.cover_bytes),
    updated_at = NOW()
FROM cover_by_user c
WHERE u.user_id = c.user_id
  AND u.source_type = 'MEDIA_LIBRARY'
  AND u.file_type = 'IMAGE';

