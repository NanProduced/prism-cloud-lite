-- V7__create_device_screenshot.sql
-- 设备截图表（设备上报截图后落库，用于预览与存储统计）

CREATE TABLE IF NOT EXISTS device_screenshot (
    screenshot_id UUID PRIMARY KEY NOT NULL,
    device_id BIGINT NOT NULL,
    s3_key VARCHAR(512) NOT NULL,
    size_bytes BIGINT NOT NULL,
    content_type VARCHAR(128),
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_device_screenshot_device FOREIGN KEY (device_id)
        REFERENCES device(device_id)
        ON DELETE CASCADE,
    CONSTRAINT uk_device_screenshot_device_s3 UNIQUE (device_id, s3_key)
);

CREATE INDEX IF NOT EXISTS idx_device_screenshot_device_uploaded_at
    ON device_screenshot(device_id, uploaded_at DESC);

