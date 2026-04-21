ALTER TABLE assistant.assistant_chat_token_usage_daily
ADD COLUMN IF NOT EXISTS frozen_tokens BIGINT NOT NULL DEFAULT 0,
ADD COLUMN IF NOT EXISTS last_frozen_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_assistant_chat_token_usage_frozen_at
ON assistant.assistant_chat_token_usage_daily (last_frozen_at)
WHERE last_frozen_at IS NOT NULL;
