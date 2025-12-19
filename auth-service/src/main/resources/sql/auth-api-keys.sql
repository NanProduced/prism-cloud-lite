-- API Keys for end-user programmatic access (client_credentials)
-- This script is intended for initial schema bootstrap in development.

CREATE TABLE IF NOT EXISTS auth_api_keys (
    id           VARCHAR(100) PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    name         VARCHAR(128) NOT NULL,
    client_id    VARCHAR(100) NOT NULL UNIQUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_api_keys_user_created
    ON auth_api_keys (user_id, created_at);

