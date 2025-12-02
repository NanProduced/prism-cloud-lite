-- V3__create_quota_usage_table.sql
-- 创建用户配额使用量表

CREATE TABLE pc_user_quota_usage (
    id UUID PRIMARY KEY NOT NULL,
    user_id UUID NOT NULL UNIQUE,
    storage_used_bytes BIGINT NOT NULL DEFAULT 0,
    device_count_active INT NOT NULL DEFAULT 0,
    program_count_active INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_pc_user_quota_user
        FOREIGN KEY (user_id)
        REFERENCES pc_user_profile(id)
        ON DELETE CASCADE
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_pc_user_quota_user_id
ON pc_user_quota_usage(user_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_pc_user_quota_user_id
ON pc_user_quota_usage(user_id);
