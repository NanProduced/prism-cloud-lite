-- V3__subscriptions.sql
-- Subscription tier (FREE/PRO) and redeem codes (demo billing simulation).

CREATE TABLE IF NOT EXISTS pca_user_subscriptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    tier VARCHAR(16) NOT NULL,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_pca_user_subscriptions_user FOREIGN KEY (user_id)
        REFERENCES pca_users(id)
        ON DELETE CASCADE,
    CONSTRAINT uk_pca_user_subscriptions_user UNIQUE (user_id)
);

CREATE INDEX IF NOT EXISTS idx_pca_user_subscriptions_user_end_at
    ON pca_user_subscriptions(user_id, end_at);

CREATE TABLE IF NOT EXISTS pca_redeem_codes (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    tier VARCHAR(16) NOT NULL,
    duration_days INT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMPTZ,
    redeemed_by UUID,
    redeemed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_pca_redeem_codes_code UNIQUE (code),
    CONSTRAINT fk_pca_redeem_codes_redeemed_by FOREIGN KEY (redeemed_by)
        REFERENCES pca_users(id)
        ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_pca_redeem_codes_enabled_expire
    ON pca_redeem_codes(enabled, expires_at);

CREATE INDEX IF NOT EXISTS idx_pca_redeem_codes_redeemed_by
    ON pca_redeem_codes(redeemed_by, redeemed_at);

CREATE TABLE IF NOT EXISTS pca_subscription_events (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    success BOOLEAN NOT NULL,
    code VARCHAR(64),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_pca_subscription_events_user FOREIGN KEY (user_id)
        REFERENCES pca_users(id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_pca_subscription_events_user_created
    ON pca_subscription_events(user_id, created_at);

-- Demo redeem codes (no real payment integration).
-- You can disable/delete these rows in real deployments.
INSERT INTO pca_redeem_codes (id, code, tier, duration_days, enabled, created_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'PRDEMO30D', 'PRO', 30, TRUE, NOW())
ON CONFLICT (code) DO NOTHING;

INSERT INTO pca_redeem_codes (id, code, tier, duration_days, enabled, created_at)
VALUES ('22222222-2222-2222-2222-222222222222', 'PRDEMO90D', 'PRO', 90, TRUE, NOW())
ON CONFLICT (code) DO NOTHING;
