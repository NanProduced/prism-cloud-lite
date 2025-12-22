-- V10__create_schedule_and_program_assignment_tables.sql
-- Schedules（排程）模块 + ProgramAssignment（节目直接下发/期望绑定）

-- =====================================================
-- Program assignment (desired binding, created by publish/unpublish)
-- =====================================================
CREATE TABLE IF NOT EXISTS pc_program_assignment (
    program_id         UUID NOT NULL,
    device_id          BIGINT NOT NULL,
    user_id            UUID NOT NULL,
    release_version    INT NOT NULL,
    release_program_id INT NOT NULL,
    assigned_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_pc_program_assignment PRIMARY KEY (program_id, device_id),
    CONSTRAINT fk_pc_program_assignment_program FOREIGN KEY (program_id)
        REFERENCES pc_program(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pc_program_assignment_user FOREIGN KEY (user_id)
        REFERENCES pc_user_profile(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pc_program_assignment_program
    ON pc_program_assignment(program_id);

CREATE INDEX IF NOT EXISTS idx_pc_program_assignment_device
    ON pc_program_assignment(user_id, device_id);

CREATE INDEX IF NOT EXISTS idx_pc_program_assignment_release_program_id
    ON pc_program_assignment(release_program_id);

-- =====================================================
-- Schedule (user-owned)
-- =====================================================
CREATE TABLE IF NOT EXISTS pc_schedule (
    schedule_id UUID PRIMARY KEY NOT NULL,
    user_id     UUID NOT NULL,
    name        VARCHAR(128) NOT NULL,
    description TEXT,
    enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pc_schedule_user FOREIGN KEY (user_id)
        REFERENCES pc_user_profile(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pc_schedule_user_id
    ON pc_schedule(user_id);

-- =====================================================
-- Device ⇄ Schedule binding (one device can bind at most one schedule)
-- =====================================================
CREATE TABLE IF NOT EXISTS pc_device_schedule_binding (
    device_id   BIGINT PRIMARY KEY NOT NULL,
    schedule_id UUID NOT NULL,
    user_id     UUID NOT NULL,
    bound_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pc_device_schedule_binding_schedule FOREIGN KEY (schedule_id)
        REFERENCES pc_schedule(schedule_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pc_device_schedule_binding_user FOREIGN KEY (user_id)
        REFERENCES pc_user_profile(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pc_device_schedule_binding_schedule
    ON pc_device_schedule_binding(schedule_id);

CREATE INDEX IF NOT EXISTS idx_pc_device_schedule_binding_user_device
    ON pc_device_schedule_binding(user_id, device_id);

-- =====================================================
-- Schedule contents rules (program schedule)
-- Notes:
-- - priority must be unique within schedule, otherwise terminal can't execute.
-- - operation.programId uses pc_program_release.device_program_id (Integer).
-- =====================================================
CREATE TABLE IF NOT EXISTS pc_schedule_contents_rule (
    id              BIGINT PRIMARY KEY NOT NULL,
    schedule_id     UUID NOT NULL,
    user_id         UUID NOT NULL,
    type            VARCHAR(16) NOT NULL, -- rotation/spot
    priority        INT NOT NULL,
    if_limit_time   BOOLEAN NOT NULL DEFAULT FALSE,
    limit_time      JSONB,
    if_limit_date   BOOLEAN NOT NULL DEFAULT FALSE,
    limit_date      JSONB,
    if_limit_weekday BOOLEAN NOT NULL DEFAULT FALSE,
    limit_weekday   JSONB,
    release_program_id INT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pc_schedule_contents_rule_schedule FOREIGN KEY (schedule_id)
        REFERENCES pc_schedule(schedule_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pc_schedule_contents_rule_user FOREIGN KEY (user_id)
        REFERENCES pc_user_profile(id)
        ON DELETE CASCADE,
    CONSTRAINT uk_pc_schedule_contents_rule_priority UNIQUE (schedule_id, priority)
);

CREATE INDEX IF NOT EXISTS idx_pc_schedule_contents_rule_schedule
    ON pc_schedule_contents_rule(schedule_id);

CREATE INDEX IF NOT EXISTS idx_pc_schedule_contents_rule_release_program_id
    ON pc_schedule_contents_rule(release_program_id);

-- =====================================================
-- Schedule command rules (command schedule)
-- Store raw payload to keep flexibility for lite stage.
-- =====================================================
CREATE TABLE IF NOT EXISTS pc_schedule_command_rule (
    id           BIGINT PRIMARY KEY NOT NULL,
    schedule_id  UUID NOT NULL,
    user_id      UUID NOT NULL,
    payload_json JSONB NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pc_schedule_command_rule_schedule FOREIGN KEY (schedule_id)
        REFERENCES pc_schedule(schedule_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pc_schedule_command_rule_user FOREIGN KEY (user_id)
        REFERENCES pc_user_profile(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pc_schedule_command_rule_schedule
    ON pc_schedule_command_rule(schedule_id);

