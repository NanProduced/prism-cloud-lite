-- V11__create_schedule_audit_log.sql
-- Schedules（排程）模块：轻量变更日志（Lite：面向用户查看排程历史）

CREATE TABLE IF NOT EXISTS pc_schedule_audit_log (
    id          BIGINT PRIMARY KEY NOT NULL,
    user_id     UUID NOT NULL,
    schedule_id UUID NOT NULL,
    action      VARCHAR(32) NOT NULL,
    details     JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pc_schedule_audit_log_user FOREIGN KEY (user_id)
        REFERENCES pc_user_profile(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pc_schedule_audit_log_schedule FOREIGN KEY (schedule_id)
        REFERENCES pc_schedule(schedule_id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pc_schedule_audit_log_schedule_time
    ON pc_schedule_audit_log(schedule_id, created_at DESC);

