-- V1__initial.sql
-- prism-core database initial schema (Flyway)

-- ---------------------------------------------------------------------------
-- USERS
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pcc_user_profile (
    id UUID PRIMARY KEY NOT NULL,
    public_id VARCHAR(64) NOT NULL UNIQUE,
    email VARCHAR(160) NOT NULL,
    phone VARCHAR(40),
    display_name VARCHAR(80),
    subscription_tier VARCHAR(32) NOT NULL DEFAULT 'FREE',
    subscription_expires_at TIMESTAMPTZ,
    metadata JSONB,
    configs JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_pcc_user_profile_tier CHECK (subscription_tier IN ('FREE', 'PRO'))
);

CREATE INDEX IF NOT EXISTS idx_pcc_user_profile_public_id
    ON pcc_user_profile(public_id);

CREATE INDEX IF NOT EXISTS idx_pcc_user_profile_email_ci
    ON pcc_user_profile (lower(email));

CREATE TABLE IF NOT EXISTS pcc_user_quota_usage (
    id UUID PRIMARY KEY NOT NULL,
    user_id UUID NOT NULL UNIQUE,
    device_count INT NOT NULL DEFAULT 0,
    program_count INT NOT NULL DEFAULT 0,
    custom_column_count INT NOT NULL DEFAULT 0,
    storage_total_bytes BIGINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_pcc_user_quota_usage_user FOREIGN KEY (user_id)
        REFERENCES pcc_user_profile(id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS pcc_user_storage_usage (
    id UUID PRIMARY KEY NOT NULL,
    user_id UUID NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    file_count INT NOT NULL DEFAULT 0,
    total_bytes BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_pcc_user_storage_usage_user FOREIGN KEY (user_id)
        REFERENCES pcc_user_profile(id)
        ON DELETE CASCADE,
    CONSTRAINT uk_pcc_user_storage_usage_user_source_file UNIQUE (user_id, source_type, file_type)
);

CREATE INDEX IF NOT EXISTS idx_pcc_user_storage_usage_user_source
    ON pcc_user_storage_usage(user_id, source_type);

-- ---------------------------------------------------------------------------
-- PLATFORM CONFIG
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pcc_platform_config (
    id UUID PRIMARY KEY NOT NULL,
    config_type VARCHAR(50) NOT NULL,
    config_key VARCHAR(100) NOT NULL,
    config_value JSONB NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_pcc_platform_config_type_key UNIQUE (config_type, config_key)
);

-- Default subscription quota (dev bootstrap).
INSERT INTO pcc_platform_config (id, config_type, config_key, config_value, enabled, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'SUBSCRIPTION_QUOTA',
    'FREE',
    '{"deviceLimit":20,"storageLimitBytes":2147483648,"programLimit":20,"programVersionLimit":100,"customColumnLimit":3}'::jsonb,
    TRUE,
    NOW(),
    NOW()
)
ON CONFLICT (config_type, config_key) DO NOTHING;

INSERT INTO pcc_platform_config (id, config_type, config_key, config_value, enabled, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    'SUBSCRIPTION_QUOTA',
    'PRO',
    '{"deviceLimit":100,"storageLimitBytes":53687091200,"programLimit":200,"programVersionLimit":1000,"customColumnLimit":20}'::jsonb,
    TRUE,
    NOW(),
    NOW()
)
ON CONFLICT (config_type, config_key) DO NOTHING;

-- ---------------------------------------------------------------------------
-- DEVICES
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pcc_device (
    device_id BIGINT PRIMARY KEY NOT NULL,
    device_name VARCHAR(128) NOT NULL,
    description VARCHAR(256),
    user_id UUID NOT NULL,
    online_status INT,
    onboarding_time TIMESTAMP,
    last_report_time TIMESTAMP,
    created_at TIMESTAMP,
    model VARCHAR(128),
    version VARCHAR(64),
    brightness INT,
    network_type INT,
    playing_program VARCHAR(256),
    resolution VARCHAR(64),
    total_storage BIGINT,
    free_storage BIGINT,
    properties JSONB,
    CONSTRAINT fk_pcc_device_user FOREIGN KEY (user_id)
        REFERENCES pcc_user_profile(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pcc_device_user_created
    ON pcc_device(user_id, created_at DESC, device_id);

CREATE TABLE IF NOT EXISTS pcc_device_screenshot (
    screenshot_id UUID PRIMARY KEY NOT NULL,
    device_id BIGINT NOT NULL,
    s3_key VARCHAR(512) NOT NULL,
    size_bytes BIGINT NOT NULL,
    content_type VARCHAR(128),
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_pcc_device_screenshot_device FOREIGN KEY (device_id)
        REFERENCES pcc_device(device_id)
        ON DELETE CASCADE,
    CONSTRAINT uk_pcc_device_screenshot_device_s3 UNIQUE (device_id, s3_key)
);

CREATE INDEX IF NOT EXISTS idx_pcc_device_screenshot_device_uploaded_at
    ON pcc_device_screenshot(device_id, uploaded_at DESC);

-- ---------------------------------------------------------------------------
-- DEVICE TAGS
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pcc_device_tag (
    tag_id BIGINT PRIMARY KEY NOT NULL,
    tag_name VARCHAR(128) NOT NULL,
    description TEXT,
    user_id UUID NOT NULL,
    slug VARCHAR(128) NOT NULL,
    color VARCHAR(255),
    icon VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pcc_device_tag_user FOREIGN KEY (user_id)
        REFERENCES pcc_user_profile(id)
        ON DELETE CASCADE,
    CONSTRAINT uk_pcc_device_tag_user_slug UNIQUE (user_id, slug)
);

CREATE INDEX IF NOT EXISTS idx_pcc_device_tag_user_created
    ON pcc_device_tag(user_id, created_at DESC, tag_id);

CREATE TABLE IF NOT EXISTS pcc_device_tag_map (
    device_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    user_id UUID NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_pcc_device_tag_map PRIMARY KEY (device_id, tag_id),
    CONSTRAINT fk_pcc_device_tag_map_device FOREIGN KEY (device_id)
        REFERENCES pcc_device(device_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pcc_device_tag_map_tag FOREIGN KEY (tag_id)
        REFERENCES pcc_device_tag(tag_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pcc_device_tag_map_user FOREIGN KEY (user_id)
        REFERENCES pcc_user_profile(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pcc_device_tag_map_user_device
    ON pcc_device_tag_map(user_id, device_id);

CREATE INDEX IF NOT EXISTS idx_pcc_device_tag_map_tag_id
    ON pcc_device_tag_map(tag_id);

-- ---------------------------------------------------------------------------
-- DEVICE CUSTOM FIELDS
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pcc_device_custom_field_def (
    field_id BIGINT PRIMARY KEY NOT NULL,
    user_id UUID NOT NULL,
    field_key VARCHAR(128) NOT NULL,
    field_type VARCHAR(32) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    description TEXT,
    icon VARCHAR(64),
    plan_tier_required BOOLEAN NOT NULL DEFAULT FALSE,
    sequence_no INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_pcc_device_custom_field_def_user FOREIGN KEY (user_id)
        REFERENCES pcc_user_profile(id)
        ON DELETE CASCADE,
    CONSTRAINT uk_pcc_device_custom_field_def_user_key UNIQUE (user_id, field_key)
);

CREATE INDEX IF NOT EXISTS idx_pcc_device_custom_field_def_user_id
    ON pcc_device_custom_field_def(user_id);

CREATE TABLE IF NOT EXISTS pcc_device_custom_field_option (
    option_id BIGINT PRIMARY KEY NOT NULL,
    field_id BIGINT NOT NULL,
    option_key VARCHAR(128) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    description TEXT,
    sequence_no INT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    color VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_pcc_device_custom_field_option_field FOREIGN KEY (field_id)
        REFERENCES pcc_device_custom_field_def(field_id)
        ON DELETE CASCADE,
    CONSTRAINT uk_pcc_device_custom_field_option_key UNIQUE (field_id, option_key)
);

CREATE INDEX IF NOT EXISTS idx_pcc_device_custom_field_option_field_id
    ON pcc_device_custom_field_option(field_id);

CREATE TABLE IF NOT EXISTS pcc_device_custom_field_value (
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
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_pcc_device_custom_field_value_user FOREIGN KEY (user_id)
        REFERENCES pcc_user_profile(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pcc_device_custom_field_value_device FOREIGN KEY (device_id)
        REFERENCES pcc_device(device_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pcc_device_custom_field_value_field FOREIGN KEY (field_id)
        REFERENCES pcc_device_custom_field_def(field_id)
        ON DELETE CASCADE,
    CONSTRAINT uk_pcc_device_custom_field_value_unique UNIQUE (user_id, device_id, field_id)
);

CREATE INDEX IF NOT EXISTS idx_pcc_device_custom_field_value_device
    ON pcc_device_custom_field_value(user_id, device_id);

CREATE INDEX IF NOT EXISTS idx_pcc_device_custom_field_value_user_field
    ON pcc_device_custom_field_value(user_id, field_id);

CREATE INDEX IF NOT EXISTS idx_pcc_device_custom_field_value_field_text
    ON pcc_device_custom_field_value(field_id, value_text);

CREATE INDEX IF NOT EXISTS idx_pcc_device_custom_field_value_field_number
    ON pcc_device_custom_field_value(field_id, value_number);

-- ---------------------------------------------------------------------------
-- PROGRAMS
-- ---------------------------------------------------------------------------

CREATE SEQUENCE IF NOT EXISTS pcc_program_device_program_id_seq AS INTEGER START WITH 1000000;

CREATE TABLE IF NOT EXISTS pcc_program (
    id UUID PRIMARY KEY NOT NULL,
    user_id UUID NOT NULL,
    name VARCHAR(128) NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    default_version INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_pcc_program_user FOREIGN KEY (user_id)
        REFERENCES pcc_user_profile(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pcc_program_user_updated
    ON pcc_program(user_id, updated_at DESC, id);

CREATE TABLE IF NOT EXISTS pcc_program_draft (
    draft_id UUID PRIMARY KEY NOT NULL,
    program_id UUID NOT NULL,
    base_version INT NOT NULL,
    vsn_json JSONB NOT NULL,
    cover_object_key VARCHAR(512),
    cover_content_type VARCHAR(128),
    cover_size_bytes BIGINT,
    content_hash VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_pcc_program_draft_program_base UNIQUE (program_id, base_version),
    CONSTRAINT fk_pcc_program_draft_program FOREIGN KEY (program_id)
        REFERENCES pcc_program(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pcc_program_draft_program_updated
    ON pcc_program_draft(program_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS pcc_program_release (
    device_program_id INT PRIMARY KEY NOT NULL DEFAULT nextval('pcc_program_device_program_id_seq'),
    program_id UUID NOT NULL,
    version INT NOT NULL,
    device_title_snapshot VARCHAR(160) NOT NULL,
    source_draft_id UUID,
    vsn_json JSONB NOT NULL,
    vsn_object_key VARCHAR(255),
    vsn_md5 VARCHAR(64) NOT NULL,
    vsn_size_bytes BIGINT NOT NULL,
    cover_object_key VARCHAR(512),
    cover_content_type VARCHAR(128),
    cover_size_bytes BIGINT,
    manifest_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_pcc_program_release_program_version UNIQUE (program_id, version),
    CONSTRAINT fk_pcc_program_release_program FOREIGN KEY (program_id)
        REFERENCES pcc_program(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pcc_program_release_program_version_desc
    ON pcc_program_release(program_id, version DESC);

CREATE INDEX IF NOT EXISTS idx_pcc_program_release_vsn_md5_size_program
    ON pcc_program_release(vsn_md5, vsn_size_bytes, program_id);

CREATE TABLE IF NOT EXISTS pcc_program_assignment (
    program_id UUID NOT NULL,
    device_id BIGINT NOT NULL,
    user_id UUID NOT NULL,
    release_version INT NOT NULL,
    release_program_id INT NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_pcc_program_assignment PRIMARY KEY (program_id, device_id),
    CONSTRAINT fk_pcc_program_assignment_program FOREIGN KEY (program_id)
        REFERENCES pcc_program(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pcc_program_assignment_device FOREIGN KEY (device_id)
        REFERENCES pcc_device(device_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pcc_program_assignment_user FOREIGN KEY (user_id)
        REFERENCES pcc_user_profile(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pcc_program_assignment_release FOREIGN KEY (release_program_id)
        REFERENCES pcc_program_release(device_program_id)
        ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_pcc_program_assignment_program_time
    ON pcc_program_assignment(program_id, assigned_at DESC);

CREATE INDEX IF NOT EXISTS idx_pcc_program_assignment_device_time
    ON pcc_program_assignment(device_id, assigned_at DESC);

CREATE INDEX IF NOT EXISTS idx_pcc_program_assignment_device_release_program
    ON pcc_program_assignment(device_id, release_program_id);

CREATE TABLE IF NOT EXISTS pcc_program_deployment (
    program_id UUID NOT NULL,
    device_id BIGINT NOT NULL,
    user_id UUID NOT NULL,
    release_version INT NOT NULL,
    release_program_id INT NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    status VARCHAR(32) NOT NULL DEFAULT 'DOWNLOADING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_pcc_program_deployment PRIMARY KEY (program_id, device_id),
    CONSTRAINT fk_pcc_program_deployment_program FOREIGN KEY (program_id)
        REFERENCES pcc_program(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pcc_program_deployment_device FOREIGN KEY (device_id)
        REFERENCES pcc_device(device_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pcc_program_deployment_user FOREIGN KEY (user_id)
        REFERENCES pcc_user_profile(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pcc_program_deployment_release FOREIGN KEY (release_program_id)
        REFERENCES pcc_program_release(device_program_id)
        ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_pcc_program_deployment_program_time
    ON pcc_program_deployment(program_id, assigned_at DESC);

CREATE INDEX IF NOT EXISTS idx_pcc_program_deployment_device_time
    ON pcc_program_deployment(device_id, assigned_at DESC);

CREATE INDEX IF NOT EXISTS idx_pcc_program_deployment_user_time
    ON pcc_program_deployment(user_id, assigned_at DESC);

CREATE INDEX IF NOT EXISTS idx_pcc_program_deployment_device_release_program
    ON pcc_program_deployment(device_id, release_program_id);

CREATE TABLE IF NOT EXISTS pcc_program_template (
    template_id UUID PRIMARY KEY NOT NULL,
    user_id UUID NOT NULL,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    width INT NOT NULL,
    height INT NOT NULL,
    vsn_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_pcc_program_template_user FOREIGN KEY (user_id)
        REFERENCES pcc_user_profile(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pcc_program_template_user_updated
    ON pcc_program_template(user_id, updated_at DESC, template_id);

CREATE TABLE IF NOT EXISTS pcc_program_audit_log (
    id BIGINT PRIMARY KEY NOT NULL,
    user_id UUID NOT NULL,
    program_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    details JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_pcc_program_audit_log_user FOREIGN KEY (user_id)
        REFERENCES pcc_user_profile(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pcc_program_audit_log_program FOREIGN KEY (program_id)
        REFERENCES pcc_program(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pcc_program_audit_log_program_time
    ON pcc_program_audit_log(program_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- SCHEDULES
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pcc_schedule (
    schedule_id UUID PRIMARY KEY NOT NULL,
    user_id UUID NOT NULL,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_pcc_schedule_user FOREIGN KEY (user_id)
        REFERENCES pcc_user_profile(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pcc_schedule_user_updated
    ON pcc_schedule(user_id, updated_at DESC, schedule_id);

CREATE TABLE IF NOT EXISTS pcc_device_schedule_binding (
    device_id BIGINT PRIMARY KEY NOT NULL,
    schedule_id UUID NOT NULL,
    user_id UUID NOT NULL,
    bound_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_pcc_device_schedule_binding_device FOREIGN KEY (device_id)
        REFERENCES pcc_device(device_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pcc_device_schedule_binding_schedule FOREIGN KEY (schedule_id)
        REFERENCES pcc_schedule(schedule_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pcc_device_schedule_binding_user FOREIGN KEY (user_id)
        REFERENCES pcc_user_profile(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pcc_device_schedule_binding_schedule
    ON pcc_device_schedule_binding(schedule_id);

CREATE TABLE IF NOT EXISTS pcc_schedule_contents_rule (
    id BIGINT PRIMARY KEY NOT NULL,
    schedule_id UUID NOT NULL,
    user_id UUID NOT NULL,
    type VARCHAR(16) NOT NULL,
    priority INT NOT NULL,
    if_limit_time BOOLEAN NOT NULL DEFAULT FALSE,
    limit_time JSONB,
    if_limit_date BOOLEAN NOT NULL DEFAULT FALSE,
    limit_date JSONB,
    if_limit_weekday BOOLEAN NOT NULL DEFAULT FALSE,
    limit_weekday JSONB,
    release_program_id INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_pcc_schedule_contents_rule_schedule FOREIGN KEY (schedule_id)
        REFERENCES pcc_schedule(schedule_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pcc_schedule_contents_rule_user FOREIGN KEY (user_id)
        REFERENCES pcc_user_profile(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pcc_schedule_contents_rule_release FOREIGN KEY (release_program_id)
        REFERENCES pcc_program_release(device_program_id)
        ON DELETE RESTRICT,
    CONSTRAINT uk_pcc_schedule_contents_rule_priority UNIQUE (schedule_id, priority)
);

CREATE INDEX IF NOT EXISTS idx_pcc_schedule_contents_rule_schedule_release_program
    ON pcc_schedule_contents_rule(schedule_id, release_program_id);

CREATE TABLE IF NOT EXISTS pcc_schedule_command_rule (
    id BIGINT PRIMARY KEY NOT NULL,
    schedule_id UUID NOT NULL,
    user_id UUID NOT NULL,
    payload_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_pcc_schedule_command_rule_schedule FOREIGN KEY (schedule_id)
        REFERENCES pcc_schedule(schedule_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pcc_schedule_command_rule_user FOREIGN KEY (user_id)
        REFERENCES pcc_user_profile(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pcc_schedule_command_rule_schedule_updated
    ON pcc_schedule_command_rule(schedule_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS pcc_schedule_audit_log (
    id BIGINT PRIMARY KEY NOT NULL,
    user_id UUID NOT NULL,
    schedule_id UUID NOT NULL,
    action VARCHAR(32) NOT NULL,
    details JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_pcc_schedule_audit_log_user FOREIGN KEY (user_id)
        REFERENCES pcc_user_profile(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pcc_schedule_audit_log_schedule FOREIGN KEY (schedule_id)
        REFERENCES pcc_schedule(schedule_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pcc_schedule_audit_log_schedule_time
    ON pcc_schedule_audit_log(schedule_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- TELEMETRY
-- ---------------------------------------------------------------------------

-- Online sessions (GiST range index on generated period)
CREATE TABLE IF NOT EXISTS pcc_device_online_session (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    device_id BIGINT NOT NULL,
    online_at TIMESTAMPTZ NOT NULL,
    offline_at TIMESTAMPTZ NOT NULL,
    period TSTZRANGE GENERATED ALWAYS AS (tstzrange(online_at, offline_at, '[)')) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_pcc_device_online_session_time_order CHECK (online_at < offline_at),
    CONSTRAINT uk_pcc_device_online_session_unique UNIQUE (device_id, online_at, offline_at)
);

CREATE INDEX IF NOT EXISTS idx_pcc_device_online_session_period_gist
    ON pcc_device_online_session USING GIST (period);

CREATE INDEX IF NOT EXISTS idx_pcc_device_online_session_user_device
    ON pcc_device_online_session (user_id, device_id);

CREATE INDEX IF NOT EXISTS idx_pcc_device_online_session_user_device_online_at
    ON pcc_device_online_session (user_id, device_id, online_at DESC, id DESC);

-- Program play sessions
CREATE TABLE IF NOT EXISTS pcc_device_program_play_session (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    device_id BIGINT NOT NULL,
    is_lan BOOLEAN NOT NULL DEFAULT FALSE,
    lan_program_id VARCHAR(64),
    program_id UUID,
    release_version INT,
    program_vsn VARCHAR(320),
    program_name_snapshot VARCHAR(256),
    vsn_md5 VARCHAR(64),
    vsn_size_bytes BIGINT,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    period TSTZRANGE GENERATED ALWAYS AS (tstzrange(start_at, end_at, '[)')) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_pcc_device_program_play_session_time_order CHECK (start_at < end_at),
    CONSTRAINT ck_pcc_device_program_play_session_source CHECK (
        (is_lan = TRUE AND lan_program_id IS NOT NULL AND program_id IS NULL AND release_version IS NULL)
        OR
        (is_lan = FALSE AND lan_program_id IS NULL AND program_id IS NOT NULL AND release_version IS NOT NULL)
    ),
    CONSTRAINT uk_pcc_device_program_play_session_platform UNIQUE (device_id, program_id, release_version, start_at, end_at),
    CONSTRAINT uk_pcc_device_program_play_session_lan UNIQUE (device_id, lan_program_id, start_at, end_at)
);

CREATE INDEX IF NOT EXISTS idx_pcc_device_program_play_session_period_gist
    ON pcc_device_program_play_session USING GIST (period);

CREATE INDEX IF NOT EXISTS idx_pcc_device_program_play_session_user_platform
    ON pcc_device_program_play_session (user_id, program_id, release_version);

CREATE INDEX IF NOT EXISTS idx_pcc_device_program_play_session_user_lan
    ON pcc_device_program_play_session (user_id, lan_program_id);

CREATE INDEX IF NOT EXISTS idx_pcc_device_program_play_session_user_device_time
    ON pcc_device_program_play_session (user_id, device_id, start_at DESC);

-- Media play sessions
CREATE TABLE IF NOT EXISTS pcc_device_media_play_session (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    device_id BIGINT NOT NULL,
    media_id VARCHAR(64) NOT NULL,
    res_origin_name VARCHAR(256),
    res_md5_name VARCHAR(256),
    item_type VARCHAR(64),
    is_lan BOOLEAN NOT NULL DEFAULT FALSE,
    program_id UUID,
    release_version INT,
    program_vsn VARCHAR(320),
    program_name_snapshot VARCHAR(256),
    vsn_md5 VARCHAR(64),
    vsn_size_bytes BIGINT,
    page_name VARCHAR(128),
    page_index INT,
    region_name VARCHAR(128),
    region_index INT,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    period TSTZRANGE GENERATED ALWAYS AS (tstzrange(start_at, end_at, '[)')) STORED,
    reported_duration BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_pcc_device_media_play_session_time_order CHECK (start_at < end_at),
    CONSTRAINT ck_pcc_device_media_play_session_source CHECK (
        (is_lan = TRUE AND program_id IS NULL AND release_version IS NULL)
        OR
        (is_lan = FALSE AND program_id IS NOT NULL AND release_version IS NOT NULL)
    ),
    CONSTRAINT uk_pcc_device_media_play_session_unique UNIQUE (device_id, media_id, start_at, end_at)
);

CREATE INDEX IF NOT EXISTS idx_pcc_device_media_play_session_period_gist
    ON pcc_device_media_play_session USING GIST (period);

CREATE INDEX IF NOT EXISTS idx_pcc_device_media_play_session_user_media
    ON pcc_device_media_play_session (user_id, media_id);

CREATE INDEX IF NOT EXISTS idx_pcc_device_media_play_session_user_device_time
    ON pcc_device_media_play_session (user_id, device_id, start_at DESC);

-- Sensor metrics (scalar points)
CREATE TABLE IF NOT EXISTS pcc_device_sensor_metric (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    device_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    report_type VARCHAR(64) NOT NULL,
    sensor_type VARCHAR(64) NOT NULL,
    sensor_id INT,
    metric_key VARCHAR(64) NOT NULL,
    value_num DOUBLE PRECISION,
    report_time_raw VARCHAR(64),
    server_time TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pcc_device_sensor_metric_user_device_time
    ON pcc_device_sensor_metric (user_id, device_id, server_time DESC);

CREATE INDEX IF NOT EXISTS idx_pcc_device_sensor_metric_user_device_type_time
    ON pcc_device_sensor_metric (user_id, device_id, source_type, report_type, server_time DESC);

CREATE INDEX IF NOT EXISTS idx_pcc_device_sensor_metric_user_device_metric_time
    ON pcc_device_sensor_metric (user_id, device_id, report_type, metric_key, server_time DESC);

-- Receive card samples (flattened rows)
CREATE TABLE IF NOT EXISTS pcc_device_receive_card_sample (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    device_id BIGINT NOT NULL,
    net_port_num INT,
    receive_card_num INT,
    x INT,
    y INT,
    width INT,
    height INT,
    bit_error_rate DOUBLE PRECISION,
    temperature INT,
    humidity INT,
    smoke DOUBLE PRECISION,
    report_time_raw VARCHAR(64),
    server_time TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pcc_device_receive_card_user_device_time
    ON pcc_device_receive_card_sample (user_id, device_id, server_time DESC);

CREATE INDEX IF NOT EXISTS idx_pcc_device_receive_card_user_device_card_time
    ON pcc_device_receive_card_sample (user_id, device_id, net_port_num, receive_card_num, server_time DESC);

-- GPS points (device reported)
CREATE TABLE IF NOT EXISTS pcc_device_gps_point (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    device_id BIGINT NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    accuracy REAL,
    altitude REAL,
    speed REAL,
    direct DOUBLE PRECISION,
    satellites INT,
    report_time_raw VARCHAR(64),
    server_time TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    extra JSONB
);

CREATE INDEX IF NOT EXISTS idx_pcc_device_gps_point_user_device_time
    ON pcc_device_gps_point (user_id, device_id, server_time DESC);

CREATE INDEX IF NOT EXISTS idx_pcc_device_gps_point_user_time
    ON pcc_device_gps_point (user_id, server_time DESC);

-- Manual location overrides (per device)
CREATE TABLE IF NOT EXISTS pcc_device_location_override (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    device_id BIGINT NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_pcc_device_location_override UNIQUE (user_id, device_id),
    CONSTRAINT fk_pcc_device_location_override_user FOREIGN KEY (user_id)
        REFERENCES pcc_user_profile(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pcc_device_location_override_device FOREIGN KEY (device_id)
        REFERENCES pcc_device(device_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pcc_device_location_override_user_device
    ON pcc_device_location_override (user_id, device_id);

-- ---------------------------------------------------------------------------
-- DEVICE COMMAND LOGS
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pcc_device_command_log (
    id BIGINT PRIMARY KEY NOT NULL,
    user_id UUID NOT NULL,
    device_id BIGINT NOT NULL,
    operation_id VARCHAR(255) NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    tracking_level VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    payload JSONB,
    ttl_minutes BIGINT,
    send_method VARCHAR(32),
    queued_id INT,
    accepted BOOLEAN NOT NULL DEFAULT FALSE,
    covered BOOLEAN NOT NULL DEFAULT FALSE,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_pcc_device_command_log_user FOREIGN KEY (user_id)
        REFERENCES pcc_user_profile(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pcc_device_command_log_device FOREIGN KEY (device_id)
        REFERENCES pcc_device(device_id)
        ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_pcc_device_command_log_operation_id
    ON pcc_device_command_log(operation_id);

CREATE INDEX IF NOT EXISTS idx_pcc_device_command_log_device_queued_time
    ON pcc_device_command_log(device_id, queued_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- DEVICE LOGS
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pcc_device_log (
    id BIGINT PRIMARY KEY NOT NULL,
    user_id UUID NOT NULL,
    device_id BIGINT NOT NULL,
    operation_id INT NOT NULL,
    level INT NOT NULL,
    log_type VARCHAR(64),
    log_subtype1 VARCHAR(64),
    log_subtype2 VARCHAR(64),
    log_subtype3 VARCHAR(64),
    categories VARCHAR(64),
    description TEXT,
    device_time_raw VARCHAR(64),
    hand_status INT,
    hand_time_raw VARCHAR(64),
    log_arg1 TEXT,
    log_arg2 TEXT,
    log_arg3 TEXT,
    log_arg4 TEXT,
    log_arg5 TEXT,
    log_arg6 TEXT,
    others TEXT,
    report_time TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_pcc_device_log_user FOREIGN KEY (user_id)
        REFERENCES pcc_user_profile(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pcc_device_log_device FOREIGN KEY (device_id)
        REFERENCES pcc_device(device_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pcc_device_log_user_time
    ON pcc_device_log(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pcc_device_log_user_device_time
    ON pcc_device_log(user_id, device_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pcc_device_log_user_operation_time
    ON pcc_device_log(user_id, operation_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pcc_device_log_created_at
    ON pcc_device_log(created_at);

-- ---------------------------------------------------------------------------
-- MESSAGE CENTER
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pcc_message (
    id UUID PRIMARY KEY NOT NULL,
    user_id UUID NOT NULL,
    kind VARCHAR(32) NOT NULL,
    type VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    title VARCHAR(256) NOT NULL,
    summary VARCHAR(512),
    payload JSONB,
    device_id BIGINT,
    program_id UUID,
    operation_id VARCHAR(128),
    task_id VARCHAR(128),
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_pcc_message_user FOREIGN KEY (user_id)
        REFERENCES pcc_user_profile(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pcc_message_user_time
    ON pcc_message (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pcc_message_user_kind_time
    ON pcc_message (user_id, kind, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pcc_message_user_type_time
    ON pcc_message (user_id, type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pcc_message_user_status_time
    ON pcc_message (user_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pcc_message_user_device_time
    ON pcc_message (user_id, device_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pcc_message_user_program_time
    ON pcc_message (user_id, program_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pcc_message_user_unread_time
    ON pcc_message (user_id, created_at DESC)
    WHERE read_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_pcc_message_created_at
    ON pcc_message(created_at);

-- ---------------------------------------------------------------------------
-- MEDIA LIBRARY
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS pcc_file_entity (
    file_id VARCHAR(255) PRIMARY KEY NOT NULL,
    md5 VARCHAR(255) UNIQUE,
    s3_key VARCHAR(255) NOT NULL,
    size BIGINT NOT NULL,
    mime_type VARCHAR(255) NOT NULL,
    ref_count INT NOT NULL DEFAULT 1,
    width INT,
    height INT,
    duration_ms BIGINT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS pcc_media_folder (
    folder_id VARCHAR(255) PRIMARY KEY NOT NULL,
    user_id UUID NOT NULL,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(128),
    parent_folder_id VARCHAR(255),
    path VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_pcc_media_folder_user FOREIGN KEY (user_id)
        REFERENCES pcc_user_profile(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pcc_media_folder_parent FOREIGN KEY (parent_folder_id)
        REFERENCES pcc_media_folder(folder_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pcc_media_folder_user_parent
    ON pcc_media_folder(user_id, parent_folder_id);

CREATE INDEX IF NOT EXISTS idx_pcc_media_folder_user_path
    ON pcc_media_folder(user_id, path);

CREATE TABLE IF NOT EXISTS pcc_media_asset (
    id VARCHAR(255) PRIMARY KEY NOT NULL,
    user_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(128),
    folder_id VARCHAR(255),
    group_id VARCHAR(255) NOT NULL,
    original_file_id VARCHAR(255) NOT NULL,
    cover_file_id VARCHAR(255),
    source_type INT NOT NULL,
    source_task_id VARCHAR(255),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_pcc_media_asset_user FOREIGN KEY (user_id)
        REFERENCES pcc_user_profile(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pcc_media_asset_folder FOREIGN KEY (folder_id)
        REFERENCES pcc_media_folder(folder_id)
        ON DELETE SET NULL,
    CONSTRAINT fk_pcc_media_asset_original_file FOREIGN KEY (original_file_id)
        REFERENCES pcc_file_entity(file_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_pcc_media_asset_cover_file FOREIGN KEY (cover_file_id)
        REFERENCES pcc_file_entity(file_id)
        ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_pcc_media_asset_user_folder
    ON pcc_media_asset(user_id, folder_id);

CREATE INDEX IF NOT EXISTS idx_pcc_media_asset_group
    ON pcc_media_asset(group_id);
