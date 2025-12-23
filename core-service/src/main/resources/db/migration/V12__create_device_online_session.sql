-- V12__create_device_online_session.sql
-- 设备在线区间（上线-下线）事实表：用于在线时长聚合（按范围/按天/按周等）

CREATE TABLE IF NOT EXISTS device_online_session (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    device_id BIGINT NOT NULL,
    online_at TIMESTAMPTZ NOT NULL,
    offline_at TIMESTAMPTZ NOT NULL,
    period TSTZRANGE GENERATED ALWAYS AS (tstzrange(online_at, offline_at, '[)')) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_device_online_session_time_order CHECK (online_at < offline_at),
    CONSTRAINT uk_device_online_session_unique UNIQUE (device_id, online_at, offline_at)
);

-- 查询时的首要过滤条件：period && tstzrange(:from,:to)
CREATE INDEX IF NOT EXISTS idx_device_online_session_period_gist
    ON device_online_session USING GIST (period);

-- 常用过滤维度
CREATE INDEX IF NOT EXISTS idx_device_online_session_user_device
    ON device_online_session (user_id, device_id);

-- 会话明细/统计常用排序维度
CREATE INDEX IF NOT EXISTS idx_device_online_session_device_online_at
    ON device_online_session (device_id, online_at DESC);
