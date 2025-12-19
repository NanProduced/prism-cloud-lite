-- Security audit events for Settings/Security (Security History)
-- This script is intended for initial schema bootstrap in development.

CREATE TABLE IF NOT EXISTS auth_security_events (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID        NOT NULL,
    event_type  VARCHAR(32) NOT NULL,
    success     BOOLEAN     NOT NULL DEFAULT TRUE,
    ip_address  VARCHAR(64),
    device_name VARCHAR(128),
    user_agent  VARCHAR(512),
    metadata    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_security_events_user_created
    ON auth_security_events (user_id, created_at);

CREATE INDEX IF NOT EXISTS idx_security_events_type_created
    ON auth_security_events (event_type, created_at);

