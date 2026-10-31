CREATE TABLE IF NOT EXISTS scheduling_saved_draft (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    cinema_id BIGINT NOT NULL,
    target_date DATE NOT NULL,
    content JSON NOT NULL,
    saved_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_scheduling_draft_scope (user_id, cinema_id, target_date),
    KEY idx_scheduling_draft_user_saved (user_id, saved_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员暂存的未发布排片草案';
