-- V13__assistant_ai_credentials.sql
-- User-provided LLM credentials (BYOK) for AI assistant

CREATE SCHEMA IF NOT EXISTS assistant;

CREATE TABLE IF NOT EXISTS assistant.user_ai_model_config (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL, -- local-vllm | openai | gemini | ...
    model VARCHAR(128) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,

    api_key_ciphertext TEXT NULL,
    api_key_last4 VARCHAR(8) NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_assistant_user_ai_model_config_user_provider
    ON assistant.user_ai_model_config(user_id, provider);

CREATE INDEX IF NOT EXISTS idx_assistant_user_ai_model_config_user_id
    ON assistant.user_ai_model_config(user_id);

CREATE INDEX IF NOT EXISTS idx_assistant_user_ai_model_config_user_provider
    ON assistant.user_ai_model_config(user_id, provider);

-- Only one default per user.
CREATE UNIQUE INDEX IF NOT EXISTS uk_assistant_user_ai_model_config_user_default
    ON assistant.user_ai_model_config(user_id)
    WHERE is_default = TRUE;
