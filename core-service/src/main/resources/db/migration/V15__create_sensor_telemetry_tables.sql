-- V15__create_sensor_telemetry_tables.sql
-- Sensor telemetry: metrics, receive-card samples, GPS points, and manual location overrides.

-- =====================================================
-- Generic sensor metric point table (scalar metrics)
-- =====================================================
CREATE TABLE IF NOT EXISTS pc_device_sensor_metric (
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

CREATE INDEX IF NOT EXISTS idx_pc_device_sensor_metric_user_device_time
    ON pc_device_sensor_metric (user_id, device_id, server_time DESC);

CREATE INDEX IF NOT EXISTS idx_pc_device_sensor_metric_user_device_type_time
    ON pc_device_sensor_metric (user_id, device_id, source_type, report_type, server_time DESC);

CREATE INDEX IF NOT EXISTS idx_pc_device_sensor_metric_user_device_metric_time
    ON pc_device_sensor_metric (user_id, device_id, report_type, metric_key, server_time DESC);

-- =====================================================
-- Receive card samples (flattened rows)
-- =====================================================
CREATE TABLE IF NOT EXISTS pc_device_receive_card_sample (
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

CREATE INDEX IF NOT EXISTS idx_pc_device_receive_card_user_device_time
    ON pc_device_receive_card_sample (user_id, device_id, server_time DESC);

CREATE INDEX IF NOT EXISTS idx_pc_device_receive_card_user_device_card_time
    ON pc_device_receive_card_sample (user_id, device_id, net_port_num, receive_card_num, server_time DESC);

-- =====================================================
-- GPS points (device reported)
-- =====================================================
CREATE TABLE IF NOT EXISTS pc_device_gps_point (
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

CREATE INDEX IF NOT EXISTS idx_pc_device_gps_point_user_device_time
    ON pc_device_gps_point (user_id, device_id, server_time DESC);

CREATE INDEX IF NOT EXISTS idx_pc_device_gps_point_user_time
    ON pc_device_gps_point (user_id, server_time DESC);

-- =====================================================
-- Manual location overrides (per device)
-- =====================================================
CREATE TABLE IF NOT EXISTS device_location_override (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    device_id BIGINT NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_device_location_override UNIQUE (user_id, device_id)
);

CREATE INDEX IF NOT EXISTS idx_device_location_override_user_device
    ON device_location_override (user_id, device_id);

