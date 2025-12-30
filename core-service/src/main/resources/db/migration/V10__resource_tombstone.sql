-- V10__resource_tombstone.sql
-- Resource tombstone/snapshot for deleted entities referenced by telemetry (60 days retention)

CREATE TABLE IF NOT EXISTS pcc_resource_tombstone (
    user_id UUID NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    ref_id VARCHAR(128) NOT NULL,
    ref_version INT NOT NULL DEFAULT 0,
    display_name VARCHAR(255),
    deleted_at TIMESTAMPTZ NOT NULL,
    purge_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_pcc_resource_tombstone PRIMARY KEY (user_id, resource_type, ref_id, ref_version)
);

CREATE INDEX IF NOT EXISTS idx_pcc_resource_tombstone_user_type_ref
    ON pcc_resource_tombstone(user_id, resource_type, ref_id);

CREATE INDEX IF NOT EXISTS idx_pcc_resource_tombstone_purge_at
    ON pcc_resource_tombstone(purge_at);

