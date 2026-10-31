CREATE TABLE `assistant_conversation` (
  `id` VARCHAR(36) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `title` VARCHAR(80) NOT NULL,
  `active_request_id` VARCHAR(36) NULL,
  `busy_until` DATETIME NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_assistant_owner_updated` (`user_id`, `update_time`, `id`),
  CONSTRAINT `fk_assistant_owner` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `assistant_turn` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `conversation_id` VARCHAR(36) NOT NULL,
  `request_id` VARCHAR(36) NOT NULL,
  `question` VARCHAR(500) NOT NULL,
  `answer` MEDIUMTEXT NOT NULL,
  `source` VARCHAR(20) NOT NULL DEFAULT 'unavailable',
  `status` VARCHAR(20) NOT NULL DEFAULT 'pending',
  `tool_results` MEDIUMTEXT NOT NULL,
  `suggestions` TEXT NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_assistant_request` (`conversation_id`, `request_id`),
  KEY `idx_assistant_turns` (`conversation_id`, `id`),
  CONSTRAINT `fk_assistant_turn_conversation` FOREIGN KEY (`conversation_id`)
    REFERENCES `assistant_conversation` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
