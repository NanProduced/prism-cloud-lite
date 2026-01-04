-- Ensure subscription quota defaults are initialized (or repaired) in pcc_platform_config.
--
-- Rationale:
-- - If SUBSCRIPTION_QUOTA rows are missing/disabled, core-service falls back to code defaults.
-- - Historical defaults used smaller Pro storage (10GiB) which is not desired (should be 50GiB).
-- - This migration is idempotent and only updates rows that look like legacy defaults.

-- FREE: 2GiB storage, devices 20, programs 20, program versions 100, custom columns 3
INSERT INTO pcc_platform_config (id, config_type, config_key, config_value, enabled, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'SUBSCRIPTION_QUOTA',
    'FREE',
    '{"deviceLimit":20,"storageLimitBytes":2147483648,"programLimit":20,"programVersionLimit":100,"customColumnLimit":3}'::jsonb,
    TRUE,
    NOW(),
    NOW()
)
ON CONFLICT (config_type, config_key) DO UPDATE
SET
    config_value = EXCLUDED.config_value,
    enabled = TRUE,
    updated_at = NOW()
WHERE
    -- update only if missing / legacy defaults
    pcc_platform_config.enabled IS DISTINCT FROM TRUE
    OR (pcc_platform_config.config_value->>'programLimit') = '50'
    OR (pcc_platform_config.config_value->>'programVersionLimit') = '5'
    OR (pcc_platform_config.config_value->>'customColumnLimit') = '2';

-- PRO: 50GiB storage, devices 100, programs 200, program versions 1000, custom columns 20
INSERT INTO pcc_platform_config (id, config_type, config_key, config_value, enabled, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    'SUBSCRIPTION_QUOTA',
    'PRO',
    '{"deviceLimit":100,"storageLimitBytes":53687091200,"programLimit":200,"programVersionLimit":1000,"customColumnLimit":20}'::jsonb,
    TRUE,
    NOW(),
    NOW()
)
ON CONFLICT (config_type, config_key) DO UPDATE
SET
    config_value = EXCLUDED.config_value,
    enabled = TRUE,
    updated_at = NOW()
WHERE
    -- update only if missing / legacy defaults
    pcc_platform_config.enabled IS DISTINCT FROM TRUE
    OR (pcc_platform_config.config_value->>'storageLimitBytes') = '10737418240'
    OR (pcc_platform_config.config_value->>'programLimit') = '-1'
    OR (pcc_platform_config.config_value->>'programVersionLimit') = '20'
    OR (pcc_platform_config.config_value->>'customColumnLimit') = '10';

