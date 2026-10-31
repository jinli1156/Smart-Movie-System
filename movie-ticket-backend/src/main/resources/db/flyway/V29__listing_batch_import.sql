ALTER TABLE listing_draft ADD COLUMN batch_id VARCHAR(36);
CREATE INDEX idx_listing_batch_scope ON listing_draft(batch_id, cinema_id, created_at);
