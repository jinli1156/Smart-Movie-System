-- Current consolidated schema for a brand-new database.
-- Existing installations are baselined at version 6 and start with V7.

CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(50) NOT NULL,
  `password` VARCHAR(100) NOT NULL,
  `nickname` VARCHAR(50) DEFAULT NULL,
  `email` VARCHAR(100) DEFAULT NULL,
  `phone` VARCHAR(20) DEFAULT NULL,
  `avatar` VARCHAR(255) DEFAULT NULL,
  `favorite_genres` JSON DEFAULT NULL,
  `role` VARCHAR(20) NOT NULL DEFAULT 'USER',
  `status` INT NOT NULL DEFAULT 1,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_phone` (`phone`),
  KEY `idx_email` (`email`),
  KEY `idx_role_status` (`role`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `cinema` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL,
  `address` VARCHAR(255) NOT NULL,
  `lng` DECIMAL(10,7) DEFAULT NULL,
  `lat` DECIMAL(10,7) DEFAULT NULL,
  `phone` VARCHAR(20) DEFAULT NULL,
  `description` TEXT,
  `images` TEXT,
  `status` INT NOT NULL DEFAULT 1,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `hall` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `cinema_id` BIGINT NOT NULL,
  `name` VARCHAR(50) NOT NULL,
  `total_rows` INT NOT NULL,
  `total_cols` INT NOT NULL,
  `total_seats` INT NOT NULL,
  `seat_layout` TEXT DEFAULT NULL,
  `status` INT NOT NULL DEFAULT 1,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_cinema_id` (`cinema_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `movie` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(100) NOT NULL,
  `english_title` VARCHAR(100) DEFAULT NULL,
  `director` VARCHAR(100) DEFAULT NULL,
  `actors` VARCHAR(255) DEFAULT NULL,
  `genre` VARCHAR(50) DEFAULT NULL,
  `country` VARCHAR(50) DEFAULT NULL,
  `language` VARCHAR(50) DEFAULT NULL,
  `duration` INT NOT NULL,
  `release_date` DATE DEFAULT NULL,
  `poster` VARCHAR(255) DEFAULT NULL,
  `trailer` VARCHAR(255) DEFAULT NULL,
  `description` TEXT,
  `rating` DECIMAL(3,1) DEFAULT NULL,
  `status` INT NOT NULL DEFAULT 1,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_title` (`title`),
  KEY `idx_status` (`status`),
  KEY `idx_release_date` (`release_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `showtime` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `movie_id` BIGINT NOT NULL,
  `cinema_id` BIGINT NOT NULL,
  `hall_id` BIGINT NOT NULL,
  `start_time` DATETIME NOT NULL,
  `end_time` DATETIME NOT NULL,
  `base_price` DECIMAL(10,2) NOT NULL,
  `total_seats` INT NOT NULL,
  `available_seats` INT NOT NULL,
  `status` INT NOT NULL DEFAULT 0,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_movie_id` (`movie_id`),
  KEY `idx_cinema_id` (`cinema_id`),
  KEY `idx_hall_id` (`hall_id`),
  KEY `idx_start_time` (`start_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ticket_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `order_no` VARCHAR(50) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `showtime_id` BIGINT NOT NULL,
  `seats` VARCHAR(255) NOT NULL,
  `seat_count` INT NOT NULL,
  `total_price` DECIMAL(10,2) NOT NULL,
  `original_price` DECIMAL(10,2) NOT NULL,
  `discount_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  `coupon_id` BIGINT DEFAULT NULL,
  `status` INT NOT NULL DEFAULT 0,
  `ticket_code` VARCHAR(8) DEFAULT NULL,
  `is_picked` INT NOT NULL DEFAULT 0,
  `pick_time` DATETIME DEFAULT NULL,
  `pay_time` DATETIME DEFAULT NULL,
  `cancel_time` DATETIME DEFAULT NULL,
  `remark` VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_ticket_code` (`ticket_code`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_showtime_id` (`showtime_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_coupon` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `amount` DECIMAL(10,2) NOT NULL,
  `min_spend` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  `status` INT NOT NULL DEFAULT 0,
  `order_id` BIGINT DEFAULT NULL,
  `valid_from` DATETIME NOT NULL,
  `expire_time` DATETIME NOT NULL,
  `use_time` DATETIME DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_coupon_user_status` (`user_id`, `status`),
  KEY `idx_coupon_expire_time` (`expire_time`),
  KEY `idx_coupon_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `comment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `movie_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `order_id` BIGINT NOT NULL,
  `rating` INT NOT NULL,
  `content` VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_movie` (`user_id`, `movie_id`),
  KEY `idx_movie_id` (`movie_id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_movie_behavior` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `movie_id` BIGINT NOT NULL,
  `behavior_type` VARCHAR(20) NOT NULL,
  `behavior_count` INT NOT NULL DEFAULT 1,
  `active` INT NOT NULL DEFAULT 1,
  `last_occurred_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_movie_behavior` (`user_id`, `movie_id`, `behavior_type`),
  KEY `idx_behavior_user_active` (`user_id`, `active`),
  KEY `idx_behavior_movie_type` (`movie_id`, `behavior_type`),
  KEY `idx_behavior_time` (`last_occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
