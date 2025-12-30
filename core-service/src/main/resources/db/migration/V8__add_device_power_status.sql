-- Add device power status redundant field.
ALTER TABLE IF EXISTS pcc_device
    ADD COLUMN IF NOT EXISTS power_status INT;

