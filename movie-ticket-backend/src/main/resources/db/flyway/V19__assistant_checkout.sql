CREATE TABLE assistant_checkout (
  id CHAR(36) NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  showtime_id BIGINT NOT NULL,
  criteria TEXT NOT NULL,
  snapshot MEDIUMTEXT NOT NULL,
  expires_at DATETIME NOT NULL,
  order_no VARCHAR(64) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_assistant_checkout_order (order_no),
  KEY idx_assistant_checkout_user (user_id, create_time),
  CONSTRAINT fk_assistant_checkout_user FOREIGN KEY (user_id) REFERENCES `user` (id),
  CONSTRAINT fk_assistant_checkout_showtime FOREIGN KEY (showtime_id) REFERENCES showtime (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
