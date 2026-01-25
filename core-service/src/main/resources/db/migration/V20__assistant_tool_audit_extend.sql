-- V20__assistant_tool_audit_extend.sql
-- Extend assistant tool audit with trace and summary fields.

ALTER TABLE assistant.assistant_tool_audit_log
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64) NULL,
    ADD COLUMN IF NOT EXISTS input_summary TEXT NULL,
    ADD COLUMN IF NOT EXISTS output_summary TEXT NULL;
