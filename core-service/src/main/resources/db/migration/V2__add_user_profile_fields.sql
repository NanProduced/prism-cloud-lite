-- V2__add_user_profile_fields.sql
-- 为 pc_user_profile 添加订阅和套餐字段

ALTER TABLE pc_user_profile
ADD COLUMN subscription_tier VARCHAR(32) NOT NULL DEFAULT 'FREE',
ADD COLUMN subscription_expires_at TIMESTAMP;

-- 为新字段创建索引
CREATE INDEX IF NOT EXISTS idx_pc_user_profile_subscription_tier
ON pc_user_profile(subscription_tier);

CREATE INDEX IF NOT EXISTS idx_pc_user_profile_subscription_expires
ON pc_user_profile(subscription_expires_at);

-- 添加检查约束
ALTER TABLE pc_user_profile
ADD CONSTRAINT check_pc_user_profile_tier
CHECK (subscription_tier IN ('FREE', 'PRO'));
