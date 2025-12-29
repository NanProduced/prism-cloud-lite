-- Optimize editor asset list query:
-- - filter by user_id
-- - order by updated_at desc
--
-- Note: LIKE '%keyword%' on title cannot use btree effectively; this index mainly improves the base scan/order.

CREATE INDEX IF NOT EXISTS idx_media_asset_user_updated_at ON pcc_media_asset (user_id, updated_at DESC, id);

