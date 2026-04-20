-- V1__payment_tables.sql
-- 支付模块数据库表结构

-- 支付订单表
CREATE TABLE IF NOT EXISTS pca_payment_orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    order_no VARCHAR(64) UNIQUE NOT NULL,
    external_order_no VARCHAR(128),
    amount DECIMAL(10,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    status VARCHAR(32) NOT NULL,
    product_type VARCHAR(32) NOT NULL,
    price_id VARCHAR(64),
    checkout_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pca_payment_orders_user_id
    ON pca_payment_orders(user_id);

CREATE INDEX IF NOT EXISTS idx_pca_payment_orders_order_no
    ON pca_payment_orders(order_no);

CREATE INDEX IF NOT EXISTS idx_pca_payment_orders_external_order_no
    ON pca_payment_orders(external_order_no);

CREATE INDEX IF NOT EXISTS idx_pca_payment_orders_status
    ON pca_payment_orders(status);

-- 支付订阅表（同步 Paddle 订阅状态）
CREATE TABLE IF NOT EXISTS pca_payment_subscriptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    external_subscription_id VARCHAR(128) UNIQUE NOT NULL,
    tier VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_period_start TIMESTAMPTZ,
    current_period_end TIMESTAMPTZ,
    cancel_at_period_end BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pca_payment_subscriptions_user_id
    ON pca_payment_subscriptions(user_id);

CREATE INDEX IF NOT EXISTS idx_pca_payment_subscriptions_external_id
    ON pca_payment_subscriptions(external_subscription_id);

CREATE INDEX IF NOT EXISTS idx_pca_payment_subscriptions_status
    ON pca_payment_subscriptions(status);

-- Webhook 事件日志（幂等性保证）
CREATE TABLE IF NOT EXISTS pca_payment_webhook_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(128) UNIQUE NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    processed BOOLEAN DEFAULT FALSE,
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pca_payment_webhook_events_event_id
    ON pca_payment_webhook_events(event_id);

CREATE INDEX IF NOT EXISTS idx_pca_payment_webhook_events_processed
    ON pca_payment_webhook_events(processed);

CREATE INDEX IF NOT EXISTS idx_pca_payment_webhook_events_created_at
    ON pca_payment_webhook_events(created_at);

-- 支付审计日志
CREATE TABLE IF NOT EXISTS pca_payment_events (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    success BOOLEAN NOT NULL,
    order_id UUID,
    subscription_id UUID,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pca_payment_events_user_id
    ON pca_payment_events(user_id);

CREATE INDEX IF NOT EXISTS idx_pca_payment_events_created_at
    ON pca_payment_events(created_at);

CREATE INDEX IF NOT EXISTS idx_pca_payment_events_order_id
    ON pca_payment_events(order_id);

CREATE INDEX IF NOT EXISTS idx_pca_payment_events_subscription_id
    ON pca_payment_events(subscription_id);
