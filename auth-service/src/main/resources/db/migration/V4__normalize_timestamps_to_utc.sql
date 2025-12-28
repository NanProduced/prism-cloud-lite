-- V4__normalize_timestamps_to_utc.sql
-- Normalize legacy OAuth2 schema TIMESTAMP (without time zone) columns to TIMESTAMPTZ (UTC).
--
-- We interpret existing TIMESTAMP values as UTC wall-clock time.

ALTER TABLE oauth2_registered_client
    ALTER COLUMN client_id_issued_at TYPE TIMESTAMPTZ USING client_id_issued_at AT TIME ZONE 'UTC',
    ALTER COLUMN client_secret_expires_at TYPE TIMESTAMPTZ USING client_secret_expires_at AT TIME ZONE 'UTC';

ALTER TABLE oauth2_authorization
    ALTER COLUMN authorization_code_issued_at TYPE TIMESTAMPTZ USING authorization_code_issued_at AT TIME ZONE 'UTC',
    ALTER COLUMN authorization_code_expires_at TYPE TIMESTAMPTZ USING authorization_code_expires_at AT TIME ZONE 'UTC',
    ALTER COLUMN access_token_issued_at TYPE TIMESTAMPTZ USING access_token_issued_at AT TIME ZONE 'UTC',
    ALTER COLUMN access_token_expires_at TYPE TIMESTAMPTZ USING access_token_expires_at AT TIME ZONE 'UTC',
    ALTER COLUMN oidc_id_token_issued_at TYPE TIMESTAMPTZ USING oidc_id_token_issued_at AT TIME ZONE 'UTC',
    ALTER COLUMN oidc_id_token_expires_at TYPE TIMESTAMPTZ USING oidc_id_token_expires_at AT TIME ZONE 'UTC',
    ALTER COLUMN refresh_token_issued_at TYPE TIMESTAMPTZ USING refresh_token_issued_at AT TIME ZONE 'UTC',
    ALTER COLUMN refresh_token_expires_at TYPE TIMESTAMPTZ USING refresh_token_expires_at AT TIME ZONE 'UTC',
    ALTER COLUMN user_code_issued_at TYPE TIMESTAMPTZ USING user_code_issued_at AT TIME ZONE 'UTC',
    ALTER COLUMN user_code_expires_at TYPE TIMESTAMPTZ USING user_code_expires_at AT TIME ZONE 'UTC',
    ALTER COLUMN device_code_issued_at TYPE TIMESTAMPTZ USING device_code_issued_at AT TIME ZONE 'UTC',
    ALTER COLUMN device_code_expires_at TYPE TIMESTAMPTZ USING device_code_expires_at AT TIME ZONE 'UTC';

