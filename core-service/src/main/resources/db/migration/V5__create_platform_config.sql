-- V5__create_platform_config.sql
-- 创建平台配置表（订阅配额等）

CREATE TABLE IF NOT EXISTS platform_config (
    id UUID PRIMARY KEY NOT NULL,
    config_type VARCHAR(50) NOT NULL,
    config_key VARCHAR(100) NOT NULL,
    config_value JSONB NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_config_type_key UNIQUE (config_type, config_key)
);

CREATE INDEX IF NOT EXISTS idx_platform_config_type_key
    ON platform_config(config_type, config_key);

-- 默认订阅配额（可按需在后台管理中调整；应用侧也有默认回退）
INSERT INTO platform_config (id, config_type, config_key, config_value, enabled, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'SUBSCRIPTION_QUOTA',
    'FREE',
    '{"deviceLimit":20,"storageLimitBytes":2147483648,"programLimit":20,"programVersionLimit":100,"customColumnLimit":3}'::jsonb,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (config_type, config_key) DO NOTHING;

INSERT INTO platform_config (id, config_type, config_key, config_value, enabled, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    'SUBSCRIPTION_QUOTA',
    'PRO',
    '{"deviceLimit":100,"storageLimitBytes":53687091200,"programLimit":200,"programVersionLimit":1000,"customColumnLimit":20}'::jsonb,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (config_type, config_key) DO NOTHING;

