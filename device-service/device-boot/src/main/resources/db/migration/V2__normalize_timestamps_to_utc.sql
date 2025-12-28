-- V2__normalize_timestamps_to_utc.sql
-- Normalize legacy TIMESTAMP (without time zone) columns to TIMESTAMPTZ (UTC).
--
-- We interpret existing TIMESTAMP values as UTC wall-clock time.

ALTER TABLE pcd_device_account
    ALTER COLUMN first_login_time TYPE TIMESTAMPTZ USING first_login_time AT TIME ZONE 'UTC',
    ALTER COLUMN last_login_time TYPE TIMESTAMPTZ USING last_login_time AT TIME ZONE 'UTC',
    ALTER COLUMN create_time TYPE TIMESTAMPTZ USING create_time AT TIME ZONE 'UTC',
    ALTER COLUMN update_time TYPE TIMESTAMPTZ USING update_time AT TIME ZONE 'UTC';

ALTER TABLE pcd_device_account
    ALTER COLUMN create_time SET DEFAULT NOW(),
    ALTER COLUMN update_time SET DEFAULT NOW();

