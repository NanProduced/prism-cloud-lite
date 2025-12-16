-- V6__create_device_custom_fields.sql
-- 设备自定义字段（自定义列）相关表结构

CREATE TABLE IF NOT EXISTS device_custom_field_def (
    field_id BIGINT PRIMARY KEY NOT NULL,
    user_id UUID NOT NULL,
    field_key VARCHAR(128) NOT NULL,
    field_type VARCHAR(32) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    description TEXT,
    icon VARCHAR(64),
    plan_tier_required BOOLEAN NOT NULL DEFAULT FALSE,
    sequence_no INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_device_custom_field_def_user_key UNIQUE (user_id, field_key),
    CONSTRAINT fk_device_custom_field_def_user FOREIGN KEY (user_id)
        REFERENCES pc_user_profile(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_device_custom_field_def_user_id
    ON device_custom_field_def(user_id);

CREATE TABLE IF NOT EXISTS device_custom_field_option (
    option_id BIGINT PRIMARY KEY NOT NULL,
    field_id BIGINT NOT NULL,
    option_key VARCHAR(128) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    description TEXT,
    sequence_no INT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    color VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_device_custom_field_option_field FOREIGN KEY (field_id)
        REFERENCES device_custom_field_def(field_id)
        ON DELETE CASCADE,
    CONSTRAINT uk_device_custom_field_option_key UNIQUE (field_id, option_key)
);

CREATE INDEX IF NOT EXISTS idx_device_custom_field_option_field_id
    ON device_custom_field_option(field_id);

CREATE TABLE IF NOT EXISTS device_custom_field_value (
    value_id BIGINT PRIMARY KEY NOT NULL,
    user_id UUID NOT NULL,
    device_id BIGINT NOT NULL,
    field_id BIGINT NOT NULL,
    value_text TEXT,
    value_number NUMERIC,
    value_datetime TIMESTAMPTZ,
    value_boolean BOOLEAN,
    value_multi_text TEXT[],
    value_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_device_custom_field_value_user FOREIGN KEY (user_id)
        REFERENCES pc_user_profile(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_device_custom_field_value_field FOREIGN KEY (field_id)
        REFERENCES device_custom_field_def(field_id)
        ON DELETE CASCADE,
    CONSTRAINT uk_device_custom_field_value_unique UNIQUE (user_id, device_id, field_id)
);

CREATE INDEX IF NOT EXISTS idx_device_custom_field_value_device
    ON device_custom_field_value(user_id, device_id);

CREATE INDEX IF NOT EXISTS idx_device_custom_field_value_field_text
    ON device_custom_field_value(field_id, value_text);

CREATE INDEX IF NOT EXISTS idx_device_custom_field_value_field_number
    ON device_custom_field_value(field_id, value_number);

