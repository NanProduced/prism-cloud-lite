-- V2__oauth_identities.sql
-- External identity bindings (Google login, etc.)

CREATE TABLE IF NOT EXISTS pca_oauth_identities (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_subject VARCHAR(128) NOT NULL,
    email CITEXT,
    email_verified BOOLEAN,
    display_name VARCHAR(256),
    avatar_url TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_pca_oauth_identities_user FOREIGN KEY (user_id)
        REFERENCES pca_users(id)
        ON DELETE CASCADE,
    CONSTRAINT uk_pca_oauth_identities_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uk_pca_oauth_identities_provider_user UNIQUE (provider, user_id)
);

CREATE INDEX IF NOT EXISTS idx_pca_oauth_identities_user_id
    ON pca_oauth_identities(user_id);

CREATE INDEX IF NOT EXISTS idx_pca_oauth_identities_provider_subject
    ON pca_oauth_identities(provider, provider_subject);

