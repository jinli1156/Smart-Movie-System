-- v6：混合推荐与用户行为闭环（可重复执行）
USE `movie_ticket`;

ALTER TABLE `user`
  ADD COLUMN IF NOT EXISTS `favorite_genres` JSON NULL COMMENT '喜欢的电影类型 JSON 数组' AFTER `avatar`;

CREATE TABLE IF NOT EXISTS `user_movie_behavior` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '行为ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `movie_id` BIGINT NOT NULL COMMENT '电影ID',
  `behavior_type` VARCHAR(20) NOT NULL COMMENT 'VIEW/FAVORITE',
  `behavior_count` INT NOT NULL DEFAULT 1 COMMENT '累计次数',
  `active` INT NOT NULL DEFAULT 1 COMMENT '收藏等可撤销行为是否生效',
  `last_occurred_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近发生时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_movie_behavior` (`user_id`, `movie_id`, `behavior_type`),
  KEY `idx_behavior_user_active` (`user_id`, `active`),
  KEY `idx_behavior_movie_type` (`movie_id`, `behavior_type`),
  KEY `idx_behavior_time` (`last_occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户电影行为表';
