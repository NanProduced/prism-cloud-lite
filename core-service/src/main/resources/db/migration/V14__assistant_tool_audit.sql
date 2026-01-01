-- V14__assistant_tool_audit.sql
-- Audit log for assistant function calling (server-executed tools)

CREATE SCHEMA IF NOT EXISTS assistant;

CREATE TABLE IF NOT EXISTS assistant.assistant_tool_audit_log (
    id UUID PRIMARY KEY,
    user_id UUID NULL,
    tool_call_id VARCHAR(64) NULL,
    tool_name VARCHAR(128) NOT NULL,
    input_json JSONB NULL,
    success BOOLEAN NOT NULL,
    elapsed_ms BIGINT NOT NULL,
    error_message TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_assistant_tool_audit_log_user_id_created_at
    ON assistant.assistant_tool_audit_log(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_assistant_tool_audit_log_tool_name_created_at
    ON assistant.assistant_tool_audit_log(tool_name, created_at DESC);

