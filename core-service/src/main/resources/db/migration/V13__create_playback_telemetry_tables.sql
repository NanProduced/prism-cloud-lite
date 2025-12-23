-- V13__create_playback_telemetry_tables.sql
-- 播放统计（节目/素材）事实表：用于播放时长/次数的范围聚合与分桶聚合

-- =====================================================
-- Program play session (device ⇄ program release)
-- =====================================================
CREATE TABLE IF NOT EXISTS pc_device_program_play_session (
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
    CONSTRAINT ck_pc_device_program_play_session_time_order CHECK (start_at < end_at),
    CONSTRAINT ck_pc_device_program_play_session_source CHECK (
        (is_lan = TRUE AND lan_program_id IS NOT NULL AND program_id IS NULL AND release_version IS NULL)
        OR
        (is_lan = FALSE AND lan_program_id IS NULL AND program_id IS NOT NULL AND release_version IS NOT NULL)
    ),
    CONSTRAINT uk_pc_device_program_play_session_platform UNIQUE (device_id, program_id, release_version, start_at, end_at),
    CONSTRAINT uk_pc_device_program_play_session_lan UNIQUE (device_id, lan_program_id, start_at, end_at)
);

CREATE INDEX IF NOT EXISTS idx_pc_device_program_play_session_period_gist
    ON pc_device_program_play_session USING GIST (period);

CREATE INDEX IF NOT EXISTS idx_pc_device_program_play_session_user_platform
    ON pc_device_program_play_session (user_id, program_id, release_version);

CREATE INDEX IF NOT EXISTS idx_pc_device_program_play_session_user_lan
    ON pc_device_program_play_session (user_id, lan_program_id);

CREATE INDEX IF NOT EXISTS idx_pc_device_program_play_session_user_device_time
    ON pc_device_program_play_session (user_id, device_id, start_at DESC);

-- =====================================================
-- Media play session (device ⇄ media asset)
-- =====================================================
CREATE TABLE IF NOT EXISTS pc_device_media_play_session (
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
    CONSTRAINT ck_pc_device_media_play_session_time_order CHECK (start_at < end_at),
    CONSTRAINT ck_pc_device_media_play_session_source CHECK (
        (is_lan = TRUE AND program_id IS NULL AND release_version IS NULL)
        OR
        (is_lan = FALSE AND program_id IS NOT NULL AND release_version IS NOT NULL)
    ),
    CONSTRAINT uk_pc_device_media_play_session_unique UNIQUE (device_id, media_id, start_at, end_at)
);

CREATE INDEX IF NOT EXISTS idx_pc_device_media_play_session_period_gist
    ON pc_device_media_play_session USING GIST (period);

CREATE INDEX IF NOT EXISTS idx_pc_device_media_play_session_user_media
    ON pc_device_media_play_session (user_id, media_id);

CREATE INDEX IF NOT EXISTS idx_pc_device_media_play_session_user_device_time
    ON pc_device_media_play_session (user_id, device_id, start_at DESC);

