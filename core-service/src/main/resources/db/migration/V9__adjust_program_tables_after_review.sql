-- V9__adjust_program_tables_after_review.sql
-- Programs（节目）模块：根据评审调整表结构
-- 1) 草稿/版本增加封面截图字段（cover_*）
-- 2) Release 保存设备侧展示名快照（device_title_snapshot），避免重命名导致设备侧标题/文件前缀变化
-- 3) 简化发布关系（deployment）字段与状态集合

-- =====================================================
-- Draft: remove redundant name, add cover fields
-- =====================================================
ALTER TABLE IF EXISTS pc_program_draft
    DROP COLUMN IF EXISTS name;

ALTER TABLE IF EXISTS pc_program_draft
    ADD COLUMN IF NOT EXISTS cover_object_key VARCHAR(512),
    ADD COLUMN IF NOT EXISTS cover_content_type VARCHAR(128),
    ADD COLUMN IF NOT EXISTS cover_size_bytes BIGINT;

-- =====================================================
-- Release: rename snapshot column and widen length, add cover fields
-- =====================================================
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'pc_program_release' AND column_name = 'program_name_snapshot'
    )
    AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'pc_program_release' AND column_name = 'device_title_snapshot'
    ) THEN
        ALTER TABLE pc_program_release RENAME COLUMN program_name_snapshot TO device_title_snapshot;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'pc_program_release' AND column_name = 'device_title_snapshot'
    ) THEN
        -- Fallback for unexpected schemas; application will backfill on next publish.
        ALTER TABLE pc_program_release ADD COLUMN device_title_snapshot VARCHAR(160);
    END IF;

    ALTER TABLE pc_program_release ALTER COLUMN device_title_snapshot TYPE VARCHAR(160);
END $$;

ALTER TABLE IF EXISTS pc_program_release
    ADD COLUMN IF NOT EXISTS cover_object_key VARCHAR(512),
    ADD COLUMN IF NOT EXISTS cover_content_type VARCHAR(128),
    ADD COLUMN IF NOT EXISTS cover_size_bytes BIGINT;

-- =====================================================
-- Deployment: drop redundant report fields, normalize status values
-- =====================================================
ALTER TABLE IF EXISTS pc_program_deployment
    DROP COLUMN IF EXISTS last_download_report_at,
    DROP COLUMN IF EXISTS last_playing_report_at,
    DROP COLUMN IF EXISTS last_error;

UPDATE pc_program_deployment
SET status = 'DOWNLOADED'
WHERE status = 'PLAYING';

UPDATE pc_program_deployment
SET status = 'DOWNLOADING'
WHERE status NOT IN ('DOWNLOADING', 'DOWNLOADED');

ALTER TABLE IF EXISTS pc_program_deployment
    ALTER COLUMN status SET DEFAULT 'DOWNLOADING';

