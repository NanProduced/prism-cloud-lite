-- V21__assistant_audit_async_events.sql
-- Extend assistant audit tables for full tool call auditing and token usage tracking.

-- Add conversation_id field to assistant_tool_audit_log for linking related tool calls
ALTER TABLE assistant.assistant_tool_audit_log
    ADD COLUMN IF NOT EXISTS conversation_id UUID NULL,
    ADD COLUMN IF NOT EXISTS prompt_tokens INTEGER NULL,
    ADD COLUMN IF NOT EXISTS completion_tokens INTEGER NULL,
    ADD COLUMN IF NOT EXISTS total_tokens INTEGER NULL,
    ADD COLUMN IF NOT EXISTS model VARCHAR(128) NULL,
    ADD COLUMN IF NOT EXISTS provider VARCHAR(64) NULL;

-- Create token usage audit log table for detailed LLM call auditing
CREATE TABLE IF NOT EXISTS assistant.assistant_chat_token_audit_log (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    trace_id VARCHAR(64) NULL,
    prompt_tokens INTEGER NULL,
    completion_tokens INTEGER NULL,
    total_tokens INTEGER NULL,
    model VARCHAR(128) NULL,
    provider VARCHAR(64) NULL,
    input_summary TEXT NULL,
    output_summary TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Create indexes for token audit log
CREATE INDEX IF NOT EXISTS idx_assistant_chat_token_audit_log_user_id_created_at
    ON assistant.assistant_chat_token_audit_log(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_assistant_chat_token_audit_log_trace_id
    ON assistant.assistant_chat_token_audit_log(trace_id);

-- Add comment to table
COMMENT ON TABLE assistant.assistant_chat_token_audit_log IS 'Audit log for LLM token usage, tracking prompt/completion tokens per call';
