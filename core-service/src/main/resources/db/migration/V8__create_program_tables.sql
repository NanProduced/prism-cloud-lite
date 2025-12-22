-- V8__create_program_tables.sql
-- Programs（节目）模块：Program / Draft / Release / Deployment / Template / AuditLog

-- =====================================================
-- Sequence: device-side program id (Integer)
-- =====================================================
CREATE SEQUENCE IF NOT EXISTS pc_program_device_program_id_seq AS INTEGER;

-- =====================================================
-- Program container (platform concept)
-- =====================================================
CREATE TABLE IF NOT EXISTS pc_program (
    id              UUID PRIMARY KEY NOT NULL,
    user_id         UUID NOT NULL,
    name            VARCHAR(128) NOT NULL,
    width           INT NOT NULL,
    height          INT NOT NULL,
    default_version INT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pc_program_user FOREIGN KEY (user_id)
        REFERENCES pc_user_profile(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pc_program_user_id
    ON pc_program(user_id);

-- =====================================================
-- Draft (editor workspace snapshot)
-- base_version: 0=Blank, N=from release vN
-- =====================================================
CREATE TABLE IF NOT EXISTS pc_program_draft (
    draft_id      UUID PRIMARY KEY NOT NULL,
    program_id    UUID NOT NULL,
    base_version  INT NOT NULL,
    name          VARCHAR(128),
    vsn_json      JSONB NOT NULL,
    content_hash  VARCHAR(64),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pc_program_draft_program_base UNIQUE (program_id, base_version),
    CONSTRAINT fk_pc_program_draft_program FOREIGN KEY (program_id)
        REFERENCES pc_program(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pc_program_draft_program_id
    ON pc_program_draft(program_id);

CREATE INDEX IF NOT EXISTS idx_pc_program_draft_updated_at
    ON pc_program_draft(updated_at);

-- =====================================================
-- Release (immutable published snapshot)
-- device_program_id: device-side program id, used by /wp-json/wp/v2/programs & /wp-json/wp/v2/media?parent=
-- =====================================================
CREATE TABLE IF NOT EXISTS pc_program_release (
    device_program_id      INT PRIMARY KEY NOT NULL DEFAULT nextval('pc_program_device_program_id_seq'),
    program_id             UUID NOT NULL,
    version                INT NOT NULL,
    program_name_snapshot  VARCHAR(128) NOT NULL,
    source_draft_id        UUID,
    vsn_json               JSONB NOT NULL,
    vsn_object_key         VARCHAR(512),
    vsn_md5                VARCHAR(64) NOT NULL,
    vsn_size_bytes         BIGINT NOT NULL,
    manifest_json          JSONB NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pc_program_release_program_version UNIQUE (program_id, version),
    CONSTRAINT fk_pc_program_release_program FOREIGN KEY (program_id)
        REFERENCES pc_program(id)
        ON DELETE CASCADE
);

-- Align sequence start to avoid collisions on existing DB.
SELECT setval(
    'pc_program_device_program_id_seq',
    GREATEST(
        1000000,
        COALESCE((SELECT MAX(device_program_id) FROM pc_program_release), 0) + 1
    ),
    false
);

CREATE INDEX IF NOT EXISTS idx_pc_program_release_program_id
    ON pc_program_release(program_id);

CREATE INDEX IF NOT EXISTS idx_pc_program_release_program_version
    ON pc_program_release(program_id, version);

CREATE INDEX IF NOT EXISTS idx_pc_program_release_vsn_md5_size
    ON pc_program_release(vsn_md5, vsn_size_bytes);

-- =====================================================
-- Deployment (device ⇄ release)
-- =====================================================
CREATE TABLE IF NOT EXISTS pc_program_deployment (
    program_id              UUID NOT NULL,
    device_id               BIGINT NOT NULL,
    user_id                 UUID NOT NULL,
    release_version         INT NOT NULL,
    release_program_id      INT NOT NULL,
    assigned_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status                  VARCHAR(32) NOT NULL DEFAULT 'ASSIGNED',
    last_download_report_at TIMESTAMPTZ,
    last_playing_report_at  TIMESTAMPTZ,
    last_error              TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_pc_program_deployment PRIMARY KEY (program_id, device_id),
    CONSTRAINT fk_pc_program_deployment_program FOREIGN KEY (program_id)
        REFERENCES pc_program(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pc_program_deployment_user FOREIGN KEY (user_id)
        REFERENCES pc_user_profile(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pc_program_deployment_program
    ON pc_program_deployment(program_id);

CREATE INDEX IF NOT EXISTS idx_pc_program_deployment_device
    ON pc_program_deployment(user_id, device_id);

CREATE INDEX IF NOT EXISTS idx_pc_program_deployment_release_program_id
    ON pc_program_deployment(release_program_id);

-- =====================================================
-- Template (platform reuse)
-- =====================================================
CREATE TABLE IF NOT EXISTS pc_program_template (
    template_id   UUID PRIMARY KEY NOT NULL,
    user_id       UUID NOT NULL,
    name          VARCHAR(128) NOT NULL,
    description   TEXT,
    width         INT NOT NULL,
    height        INT NOT NULL,
    vsn_json      JSONB NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pc_program_template_user FOREIGN KEY (user_id)
        REFERENCES pc_user_profile(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pc_program_template_user_id
    ON pc_program_template(user_id);

-- =====================================================
-- Audit log (UI: Full Audit Trail)
-- =====================================================
CREATE TABLE IF NOT EXISTS pc_program_audit_log (
    id          BIGINT PRIMARY KEY NOT NULL,
    user_id     UUID NOT NULL,
    program_id  UUID NOT NULL,
    action      VARCHAR(32) NOT NULL,
    details     JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pc_program_audit_log_user FOREIGN KEY (user_id)
        REFERENCES pc_user_profile(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pc_program_audit_log_program FOREIGN KEY (program_id)
        REFERENCES pc_program(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pc_program_audit_log_program_time
    ON pc_program_audit_log(program_id, created_at DESC);

