CREATE TABLE marketing_campaign (
 id VARCHAR(36) PRIMARY KEY, cinema_id BIGINT NOT NULL, owner_id BIGINT NOT NULL,
 name VARCHAR(120) NOT NULL, brief VARCHAR(600) NOT NULL, audience VARCHAR(80) NOT NULL,
 styles TEXT NOT NULL, ratios TEXT NOT NULL, movie_snapshot MEDIUMTEXT NOT NULL,
 created_at DATETIME(6) NOT NULL, INDEX idx_marketing_campaign_scope (cinema_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE marketing_batch (
 id VARCHAR(36) PRIMARY KEY, campaign_id VARCHAR(36) NOT NULL, request_key VARCHAR(80) NOT NULL,
 kind VARCHAR(12) NOT NULL, created_at DATETIME(6) NOT NULL,
 UNIQUE KEY uk_marketing_batch_request (campaign_id, request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE aigc_task (
 id VARCHAR(36) PRIMARY KEY, campaign_id VARCHAR(36) NOT NULL, batch_id VARCHAR(36) NOT NULL,
 movie_id BIGINT NOT NULL, kind VARCHAR(12) NOT NULL, style VARCHAR(20), ratio VARCHAR(10),
 status VARCHAR(24) NOT NULL, attempts INT NOT NULL DEFAULT 0, provider_calls INT NOT NULL DEFAULT 0, lease_token VARCHAR(36),
 lease_until DATETIME(6), remote_url TEXT, result MEDIUMTEXT, model VARCHAR(80),
 error_code VARCHAR(40), error_message VARCHAR(160), duration_ms BIGINT,
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 INDEX idx_aigc_queue (status, created_at), INDEX idx_aigc_campaign (campaign_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE marketing_asset (
 id VARCHAR(36) PRIMARY KEY, campaign_id VARCHAR(36) NOT NULL, cinema_id BIGINT NOT NULL,
 movie_id BIGINT NOT NULL, task_id VARCHAR(36), parent_asset_id VARCHAR(36),
 storage_key VARCHAR(80) NOT NULL, background_key VARCHAR(80) NOT NULL,
 source_type VARCHAR(32) NOT NULL, model VARCHAR(80), style VARCHAR(20), ratio VARCHAR(10),
 width INT NOT NULL, height INT NOT NULL, checksum VARCHAR(64) NOT NULL,
 created_at DATETIME(6) NOT NULL, INDEX idx_marketing_asset_scope (cinema_id, campaign_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE marketing_content_version (
 id VARCHAR(36) PRIMARY KEY, campaign_id VARCHAR(36) NOT NULL, movie_id BIGINT NOT NULL,
 version INT NOT NULL, payload MEDIUMTEXT NOT NULL, facts_snapshot MEDIUMTEXT NOT NULL,
 content_hash VARCHAR(64) NOT NULL, status VARCHAR(24) NOT NULL,
 created_at DATETIME(6) NOT NULL, UNIQUE KEY uk_marketing_content_version (campaign_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE marketing_review_log (
 id VARCHAR(36) PRIMARY KEY, campaign_id VARCHAR(36) NOT NULL, content_version_id VARCHAR(36),
 operator_id BIGINT NOT NULL, action VARCHAR(32) NOT NULL, reason VARCHAR(300),
 created_at DATETIME(6) NOT NULL, INDEX idx_marketing_review_campaign (campaign_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE marketing_publication (
 id VARCHAR(36) PRIMARY KEY, content_version_id VARCHAR(36) NOT NULL, campaign_id VARCHAR(36) NOT NULL,
 cinema_id BIGINT NOT NULL, slot VARCHAR(20) NOT NULL, start_at DATETIME(6) NOT NULL,
 end_at DATETIME(6) NOT NULL, status VARCHAR(16) NOT NULL, idempotency_key VARCHAR(80) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 UNIQUE KEY uk_marketing_publish_request (idempotency_key),
 UNIQUE KEY uk_marketing_version_slot (content_version_id, slot),
 INDEX idx_marketing_placement (slot, status, start_at, end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
