-- Message Center: remove i18n-unfriendly presentation fields.
-- Frontend renders by (kind, type, status, payload) with its own i18n.

ALTER TABLE pcc_message
    DROP COLUMN IF EXISTS title,
    DROP COLUMN IF EXISTS summary;

