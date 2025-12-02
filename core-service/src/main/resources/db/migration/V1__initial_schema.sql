-- V1__initial_schema.sql
-- 初始化用户资料表

CREATE TABLE IF NOT EXISTS pc_user_profile (
    id UUID PRIMARY KEY NOT NULL,
    public_id VARCHAR(64) UNIQUE NOT NULL,
    email VARCHAR(160) NOT NULL,
    phone VARCHAR(40),
    display_name VARCHAR(80),
    metadata JSONB,
    configs JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_pc_user_profile_public_id ON pc_user_profile(public_id);
CREATE INDEX IF NOT EXISTS idx_pc_user_profile_email ON pc_user_profile(email);
