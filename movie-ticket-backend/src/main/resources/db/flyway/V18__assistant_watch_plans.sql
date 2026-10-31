ALTER TABLE assistant_conversation ADD COLUMN watch_plan MEDIUMTEXT NULL;
ALTER TABLE assistant_turn ADD COLUMN watch_plan MEDIUMTEXT NULL;

CREATE TABLE assistant_preference (
  user_id BIGINT NOT NULL PRIMARY KEY,
  preferences TEXT NOT NULL,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_assistant_preference_user FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
