-- V16__create_message_center_tables.sql
-- Message center: persisted notifications (SSE subset) and async task updates.

CREATE TABLE IF NOT EXISTS pc_message (
    id           UUID PRIMARY KEY NOT NULL,
    user_id      UUID NOT NULL,

    kind         VARCHAR(32) NOT NULL,
    type         VARCHAR(128) NOT NULL,
    status       VARCHAR(32) NOT NULL,

    title        VARCHAR(256) NOT NULL,
    summary      VARCHAR(512),
    payload      JSONB,

    device_id    BIGINT,
    program_id   UUID,
    operation_id VARCHAR(128),
    task_id      VARCHAR(128),

    read_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_pc_message_user FOREIGN KEY (user_id)
        REFERENCES pc_user_profile(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pc_message_user_time
    ON pc_message (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pc_message_user_kind_time
    ON pc_message (user_id, kind, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pc_message_user_type_time
    ON pc_message (user_id, type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pc_message_user_status_time
    ON pc_message (user_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pc_message_user_device_time
    ON pc_message (user_id, device_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pc_message_user_program_time
    ON pc_message (user_id, program_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pc_message_user_unread_time
    ON pc_message (user_id, created_at DESC)
    WHERE read_at IS NULL;

