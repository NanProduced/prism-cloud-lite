-- V1__initial.sql
-- prism-device database initial schema (Flyway)

CREATE TABLE IF NOT EXISTS pcd_device_account (
    device_id BIGINT PRIMARY KEY NOT NULL,
    account VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    account_status SMALLINT NOT NULL,
    first_login_time TIMESTAMP,
    last_login_time TIMESTAMP,
    last_login_ip VARCHAR(64),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pcd_device_account_account
    ON pcd_device_account(account);

CREATE TABLE IF NOT EXISTS pcd_device_online_time_record (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL,
    online_time TIMESTAMP NOT NULL,
    offline_time TIMESTAMP NOT NULL,
    duration BIGINT NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pcd_device_online_time_record_device_online_time
    ON pcd_device_online_time_record(device_id, online_time DESC);
