ALTER TABLE marketing_batch ADD COLUMN image_count INT NOT NULL DEFAULT 0;

-- Preserve the number originally accepted by existing batches for idempotent retries.
UPDATE marketing_batch b
SET image_count = (SELECT COUNT(*) FROM aigc_task t WHERE t.batch_id=b.id AND t.kind='IMAGE');
