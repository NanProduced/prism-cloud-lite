CREATE TABLE IF NOT EXISTS assistant.assistant_chat_token_freeze (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL,
    day             DATE        NOT NULL,
    req_id          UUID        NOT NULL,
    frozen_tokens   BIGINT      NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id),
    UNIQUE (req_id)
);

CREATE INDEX IF NOT EXISTS idx_assistant_chat_token_freeze_user_day
ON assistant.assistant_chat_token_freeze (user_id, day);

CREATE INDEX IF NOT EXISTS idx_assistant_chat_token_freeze_status
ON assistant.assistant_chat_token_freeze (status);

CREATE INDEX IF NOT EXISTS idx_assistant_chat_token_freeze_created_at
ON assistant.assistant_chat_token_freeze (created_at);

COMMENT ON TABLE assistant.assistant_chat_token_freeze IS 'AI聊天Token冻结记录，每个请求独立跟踪';
COMMENT ON COLUMN assistant.assistant_chat_token_freeze.req_id IS '唯一请求ID，用于精确追踪每次冻结操作';
COMMENT ON COLUMN assistant.assistant_chat_token_freeze.status IS '状态：ACTIVE-冻结中, SETTLED-已结算, RELEASED-已释放';
