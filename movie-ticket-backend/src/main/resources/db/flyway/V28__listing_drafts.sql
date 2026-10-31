CREATE TABLE listing_draft (
 id VARCHAR(36) PRIMARY KEY,
 kind VARCHAR(12) NOT NULL,
 cinema_id BIGINT,
 owner_id BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL,
 fields MEDIUMTEXT NOT NULL,
 extracted MEDIUMTEXT NOT NULL,
 source_text MEDIUMTEXT NOT NULL,
 source_names TEXT NOT NULL,
 source_images TEXT NOT NULL,
 extraction_mode VARCHAR(24) NOT NULL,
 extraction_warning VARCHAR(300) NOT NULL,
 review_note VARCHAR(500) NOT NULL DEFAULT '',
 reviewer_id BIGINT,
 entity_id BIGINT,
 publication_key VARCHAR(64) UNIQUE,
 version INT NOT NULL DEFAULT 1,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 reviewed_at DATETIME(6),
 INDEX idx_listing_scope (cinema_id, kind, status, created_at),
 INDEX idx_listing_owner (owner_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE listing_review_log (
 id VARCHAR(36) PRIMARY KEY, draft_id VARCHAR(36) NOT NULL, operator_id BIGINT NOT NULL,
 action VARCHAR(20) NOT NULL, note VARCHAR(500) NOT NULL, version INT NOT NULL,
 created_at DATETIME(6) NOT NULL,
 INDEX idx_listing_review (draft_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
