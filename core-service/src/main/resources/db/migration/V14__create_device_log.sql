-- V14__create_device_log.sql
-- Device logs: terminal reported logs for dashboard querying (retention handled by scheduler)

CREATE TABLE IF NOT EXISTS pc_device_log (
    id              BIGINT PRIMARY KEY NOT NULL,
    user_id         UUID NOT NULL,
    device_id       BIGINT NOT NULL,
    operation_id    INT NOT NULL,
    level           INT NOT NULL,

    log_type        VARCHAR(64),
    log_subtype1    VARCHAR(64),
    log_subtype2    VARCHAR(64),
    log_subtype3    VARCHAR(64),
    categories      VARCHAR(64),
    description     TEXT,

    device_time_raw VARCHAR(64),
    hand_status     INT,
    hand_time_raw   VARCHAR(64),

    log_arg1        TEXT,
    log_arg2        TEXT,
    log_arg3        TEXT,
    log_arg4        TEXT,
    log_arg5        TEXT,
    log_arg6        TEXT,

    others          TEXT,

    report_time     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_pc_device_log_user FOREIGN KEY (user_id)
        REFERENCES pc_user_profile(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pc_device_log_device FOREIGN KEY (device_id)
        REFERENCES device(device_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pc_device_log_user_time
    ON pc_device_log(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pc_device_log_user_device_time
    ON pc_device_log(user_id, device_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pc_device_log_user_operation_time
    ON pc_device_log(user_id, operation_id, created_at DESC);

