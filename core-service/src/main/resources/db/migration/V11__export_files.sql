-- V11__export_files.sql
-- Export files persisted to S3 and accounted in storage quota (StorageSourceType.EXPORT)

CREATE TABLE IF NOT EXISTS pcc_export_file (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    message_id UUID NOT NULL,
    task_id VARCHAR(64) NOT NULL,
    export_type VARCHAR(64) NOT NULL,
    format VARCHAR(16) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    s3_key VARCHAR(1024) NOT NULL,
    size_bytes BIGINT NOT NULL DEFAULT 0,
    row_count BIGINT,
    spec JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_pcc_export_file_task_id
    ON pcc_export_file(task_id);

CREATE INDEX IF NOT EXISTS idx_pcc_export_file_user_created_at
    ON pcc_export_file(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pcc_export_file_user_message_id
    ON pcc_export_file(user_id, message_id);

