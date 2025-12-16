-- V4__sync_quota_usage_schema.sql
-- 对齐 pc_user_quota_usage 表结构与 UserQuotaUsageEntity（device_count/program_count/custom_column_count/storage_total_bytes）

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'pc_user_quota_usage' AND column_name = 'storage_used_bytes'
    ) THEN
        ALTER TABLE pc_user_quota_usage RENAME COLUMN storage_used_bytes TO storage_total_bytes;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'pc_user_quota_usage' AND column_name = 'device_count_active'
    ) THEN
        ALTER TABLE pc_user_quota_usage RENAME COLUMN device_count_active TO device_count;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'pc_user_quota_usage' AND column_name = 'program_count_active'
    ) THEN
        ALTER TABLE pc_user_quota_usage RENAME COLUMN program_count_active TO program_count;
    END IF;
END $$;

ALTER TABLE pc_user_quota_usage
    ADD COLUMN IF NOT EXISTS custom_column_count INT NOT NULL DEFAULT 0;

ALTER TABLE pc_user_quota_usage
    ADD COLUMN IF NOT EXISTS device_count INT NOT NULL DEFAULT 0;

ALTER TABLE pc_user_quota_usage
    ADD COLUMN IF NOT EXISTS program_count INT NOT NULL DEFAULT 0;

ALTER TABLE pc_user_quota_usage
    ADD COLUMN IF NOT EXISTS storage_total_bytes BIGINT NOT NULL DEFAULT 0;

