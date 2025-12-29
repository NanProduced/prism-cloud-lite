-- Add device/program name snapshot fields to messages to avoid cross-modulith lookups at read time.

ALTER TABLE pcc_message
    ADD COLUMN IF NOT EXISTS device_name_snapshot VARCHAR(256),
    ADD COLUMN IF NOT EXISTS program_name_snapshot VARCHAR(256);

-- Best-effort backfill from current device/program tables (user-scoped join).
UPDATE pcc_message m
SET device_name_snapshot = d.device_name
FROM pcc_device d
WHERE m.device_name_snapshot IS NULL
  AND m.device_id IS NOT NULL
  AND d.device_id = m.device_id
  AND d.user_id = m.user_id;

UPDATE pcc_message m
SET program_name_snapshot = p.name
FROM pcc_program p
WHERE m.program_name_snapshot IS NULL
  AND m.program_id IS NOT NULL
  AND p.id = m.program_id
  AND p.user_id = m.user_id;

