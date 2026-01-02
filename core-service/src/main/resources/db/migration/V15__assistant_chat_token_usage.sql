CREATE TABLE IF NOT EXISTS assistant.assistant_chat_token_usage_daily (
    user_id     UUID        NOT NULL,
    day         DATE        NOT NULL,
    used_tokens BIGINT      NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, day)
);

