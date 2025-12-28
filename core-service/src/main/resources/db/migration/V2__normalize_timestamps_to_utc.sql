-- V2__normalize_timestamps_to_utc.sql
-- Normalize legacy TIMESTAMP (without time zone) columns to TIMESTAMPTZ (UTC).
--
-- Note:
-- - We interpret existing TIMESTAMP values as UTC wall-clock time.
-- - `AT TIME ZONE 'UTC'` converts "timestamp without time zone" -> "timestamptz".

-- ---------------------------------------------------------------------------
-- DEVICES
-- ---------------------------------------------------------------------------

ALTER TABLE pcc_device
    ALTER COLUMN onboarding_time TYPE TIMESTAMPTZ USING onboarding_time AT TIME ZONE 'UTC',
    ALTER COLUMN last_report_time TYPE TIMESTAMPTZ USING last_report_time AT TIME ZONE 'UTC',
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

-- Optional defaults for new rows (safe even if app sets them explicitly).
ALTER TABLE pcc_device
    ALTER COLUMN created_at SET DEFAULT NOW();

-- ---------------------------------------------------------------------------
-- DEVICE TAGS
-- ---------------------------------------------------------------------------

ALTER TABLE pcc_device_tag
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE pcc_device_tag
    ALTER COLUMN created_at SET DEFAULT NOW(),
    ALTER COLUMN updated_at SET DEFAULT NOW();

ALTER TABLE pcc_device_tag_map
    ALTER COLUMN assigned_at TYPE TIMESTAMPTZ USING assigned_at AT TIME ZONE 'UTC';

ALTER TABLE pcc_device_tag_map
    ALTER COLUMN assigned_at SET DEFAULT NOW();

