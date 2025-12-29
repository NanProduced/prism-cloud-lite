-- Speed up reference counting when deleting assets (used to decide whether to clean up file_entity + object storage).
-- PostgreSQL supports IF NOT EXISTS for CREATE INDEX.

CREATE INDEX IF NOT EXISTS idx_media_asset_original_file_id ON pcc_media_asset (original_file_id);
CREATE INDEX IF NOT EXISTS idx_media_asset_cover_file_id ON pcc_media_asset (cover_file_id);

