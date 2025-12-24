-- V1__initial.sql
-- prism-auth database initial schema (Flyway)

-- Case-insensitive text for email/phone/login aliases.
CREATE EXTENSION IF NOT EXISTS citext;

-- =====================================================
-- Auth users
-- =====================================================

CREATE TABLE IF NOT EXISTS pca_users (
    id UUID PRIMARY KEY NOT NULL,
    public_id VARCHAR(36) NOT NULL UNIQUE,
    email CITEXT NOT NULL UNIQUE,
    phone CITEXT UNIQUE,
    password_hash TEXT,
    password_algo VARCHAR(16) NOT NULL DEFAULT 'bcrypt',
    user_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pca_users_public_id ON pca_users(public_id);

CREATE TABLE IF NOT EXISTS pca_admin_users (
    id UUID PRIMARY KEY NOT NULL,
    public_id VARCHAR(36) NOT NULL UNIQUE,
    email CITEXT NOT NULL UNIQUE,
    phone CITEXT UNIQUE,
    password_hash TEXT,
    password_algo VARCHAR(16) NOT NULL DEFAULT 'bcrypt',
    user_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pca_admin_users_public_id ON pca_admin_users(public_id);

-- =====================================================
-- Login aliases (email/phone/username)
-- =====================================================

CREATE TABLE IF NOT EXISTS pca_login_alias (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    alias_type VARCHAR(16) NOT NULL,
    alias_value CITEXT NOT NULL,
    CONSTRAINT fk_pca_login_alias_user FOREIGN KEY (user_id)
        REFERENCES pca_users(id)
        ON DELETE CASCADE,
    CONSTRAINT uk_pca_login_alias_type_value UNIQUE (alias_type, alias_value)
);

CREATE INDEX IF NOT EXISTS idx_pca_login_alias_user_id ON pca_login_alias(user_id);

-- =====================================================
-- Remember-me tokens
-- =====================================================

CREATE TABLE IF NOT EXISTS pca_remember_me_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    user_type VARCHAR(16) NOT NULL,
    series VARCHAR(64) NOT NULL,
    token_hash VARCHAR(96) NOT NULL,
    device_name VARCHAR(128),
    user_agent VARCHAR(512),
    ip_address VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT uk_pca_remember_me_tokens_series UNIQUE (series)
);

CREATE INDEX IF NOT EXISTS idx_pca_remember_me_tokens_user_revoked_created
    ON pca_remember_me_tokens(user_id, revoked, created_at DESC);

-- =====================================================
-- Security events
-- =====================================================

CREATE TABLE IF NOT EXISTS pca_security_events (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    success BOOLEAN NOT NULL,
    ip_address VARCHAR(64),
    device_name VARCHAR(128),
    user_agent VARCHAR(512),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pca_security_events_user_created
    ON pca_security_events (user_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_pca_security_events_type_created
    ON pca_security_events (event_type, created_at DESC);

-- =====================================================
-- Spring Authorization Server tables (customized)
-- =====================================================

CREATE TABLE IF NOT EXISTS oauth2_registered_client (
    id varchar(100) NOT NULL,
    client_id varchar(100) NOT NULL,
    client_id_issued_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_secret varchar(200) DEFAULT NULL,
    client_secret_expires_at timestamp DEFAULT NULL,
    client_name varchar(200) NOT NULL,
    client_authentication_methods varchar(1000) NOT NULL,
    authorization_grant_types varchar(1000) NOT NULL,
    redirect_uris varchar(1000) DEFAULT NULL,
    scopes varchar(1000) NOT NULL,
    post_logout_redirect_uris varchar(1000) DEFAULT NULL,
    client_settings varchar(2000) NOT NULL,
    token_settings varchar(2000) NOT NULL,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_oauth2_registered_client_client_id
    ON oauth2_registered_client (client_id);

-- =====================================================
-- API keys metadata (maps to oauth2_registered_client.id)
-- =====================================================

CREATE TABLE IF NOT EXISTS pca_api_keys (
    id VARCHAR(100) PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(128) NOT NULL,
    client_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMPTZ,
    CONSTRAINT fk_pca_api_keys_user FOREIGN KEY (user_id)
        REFERENCES pca_users(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_pca_api_keys_registered_client FOREIGN KEY (id)
        REFERENCES oauth2_registered_client(id)
        ON DELETE CASCADE,
    CONSTRAINT uk_pca_api_keys_client_id UNIQUE (client_id)
);

CREATE INDEX IF NOT EXISTS idx_pca_api_keys_user_created
    ON pca_api_keys (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS oauth2_authorization_consent (
    registered_client_id varchar(100) NOT NULL,
    principal_name varchar(200) NOT NULL,
    authorities varchar(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);

CREATE TABLE IF NOT EXISTS oauth2_authorization (
    id varchar(100) NOT NULL,
    registered_client_id varchar(100) NOT NULL,
    principal_name varchar(200) NOT NULL,
    authorization_grant_type varchar(100) NOT NULL,
    authorized_scopes varchar(1000) DEFAULT NULL,
    attributes text DEFAULT NULL,
    state varchar(500) DEFAULT NULL,

    -- custom: session binding & login state
    session_id varchar(100) DEFAULT NULL,
    login_state smallint DEFAULT NULL,

    -- Authorization Code
    authorization_code_value text DEFAULT NULL,
    authorization_code_issued_at timestamp DEFAULT NULL,
    authorization_code_expires_at timestamp DEFAULT NULL,
    authorization_code_metadata text DEFAULT NULL,

    -- Access Token
    access_token_value text DEFAULT NULL,
    access_token_issued_at timestamp DEFAULT NULL,
    access_token_expires_at timestamp DEFAULT NULL,
    access_token_metadata text DEFAULT NULL,
    access_token_type varchar(100) DEFAULT NULL,
    access_token_scopes varchar(1000) DEFAULT NULL,

    -- OIDC ID Token
    oidc_id_token_value text DEFAULT NULL,
    oidc_id_token_issued_at timestamp DEFAULT NULL,
    oidc_id_token_expires_at timestamp DEFAULT NULL,
    oidc_id_token_metadata text DEFAULT NULL,

    -- Refresh Token
    refresh_token_value text DEFAULT NULL,
    refresh_token_issued_at timestamp DEFAULT NULL,
    refresh_token_expires_at timestamp DEFAULT NULL,
    refresh_token_metadata text DEFAULT NULL,

    -- User Code (Device Flow)
    user_code_value text DEFAULT NULL,
    user_code_issued_at timestamp DEFAULT NULL,
    user_code_expires_at timestamp DEFAULT NULL,
    user_code_metadata text DEFAULT NULL,

    -- Device Code (Device Flow)
    device_code_value text DEFAULT NULL,
    device_code_issued_at timestamp DEFAULT NULL,
    device_code_expires_at timestamp DEFAULT NULL,
    device_code_metadata text DEFAULT NULL,

    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_oauth2_authorization_registered_client_id
    ON oauth2_authorization (registered_client_id);

CREATE INDEX IF NOT EXISTS idx_oauth2_authorization_principal_name
    ON oauth2_authorization (principal_name);

CREATE INDEX IF NOT EXISTS idx_oauth2_authorization_session_id
    ON oauth2_authorization (session_id);

CREATE INDEX IF NOT EXISTS idx_oauth2_authorization_state
    ON oauth2_authorization (state);

CREATE INDEX IF NOT EXISTS idx_oauth2_authorization_authorization_code_value
    ON oauth2_authorization (authorization_code_value);

CREATE INDEX IF NOT EXISTS idx_oauth2_authorization_access_token_value
    ON oauth2_authorization (access_token_value);

CREATE INDEX IF NOT EXISTS idx_oauth2_authorization_oidc_id_token_value
    ON oauth2_authorization (oidc_id_token_value);

CREATE INDEX IF NOT EXISTS idx_oauth2_authorization_refresh_token_value
    ON oauth2_authorization (refresh_token_value);

CREATE INDEX IF NOT EXISTS idx_oauth2_authorization_user_code_value
    ON oauth2_authorization (user_code_value);

CREATE INDEX IF NOT EXISTS idx_oauth2_authorization_device_code_value
    ON oauth2_authorization (device_code_value);
