-- Validate legacy data before adding constraints. The migration stops without
-- deleting or rewriting business records when an orphan or invalid value exists.
-- Recommendation behavior is derived data: archive orphan rows before removal
-- so the migration is recoverable while the live table can accept foreign keys.
CREATE TABLE IF NOT EXISTS `migration_orphan_user_movie_behavior` (
  `id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `movie_id` BIGINT NOT NULL,
  `behavior_type` VARCHAR(20) NOT NULL,
  `behavior_count` INT NOT NULL,
  `active` INT NOT NULL,
  `last_occurred_at` DATETIME NOT NULL,
  `create_time` DATETIME NULL,
  `update_time` DATETIME NULL,
  `archive_reason` VARCHAR(100) NULL,
  `archived_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO `migration_orphan_user_movie_behavior`
    (`id`, `user_id`, `movie_id`, `behavior_type`, `behavior_count`, `active`,
     `last_occurred_at`, `create_time`, `update_time`, `archive_reason`)
SELECT b.`id`, b.`user_id`, b.`movie_id`, b.`behavior_type`, b.`behavior_count`, b.`active`,
       b.`last_occurred_at`, b.`create_time`, b.`update_time`,
       'missing user or movie before V8'
FROM `user_movie_behavior` b
LEFT JOIN `user` u ON u.id = b.user_id
LEFT JOIN `movie` m ON m.id = b.movie_id
WHERE u.id IS NULL OR m.id IS NULL;

DELETE b
FROM `user_movie_behavior` b
LEFT JOIN `user` u ON u.id = b.user_id
LEFT JOIN `movie` m ON m.id = b.movie_id
WHERE u.id IS NULL OR m.id IS NULL;

DROP PROCEDURE IF EXISTS `validate_movie_ticket_integrity`;

DELIMITER $$
CREATE PROCEDURE `validate_movie_ticket_integrity`()
BEGIN
    IF EXISTS (SELECT 1 FROM hall h LEFT JOIN cinema c ON c.id = h.cinema_id WHERE c.id IS NULL LIMIT 1)
       OR EXISTS (SELECT 1 FROM showtime s LEFT JOIN movie m ON m.id = s.movie_id WHERE m.id IS NULL LIMIT 1)
       OR EXISTS (SELECT 1 FROM showtime s LEFT JOIN cinema c ON c.id = s.cinema_id WHERE c.id IS NULL LIMIT 1)
       OR EXISTS (SELECT 1 FROM showtime s LEFT JOIN hall h ON h.id = s.hall_id WHERE h.id IS NULL LIMIT 1)
       OR EXISTS (SELECT 1 FROM ticket_order o LEFT JOIN `user` u ON u.id = o.user_id WHERE u.id IS NULL LIMIT 1)
       OR EXISTS (SELECT 1 FROM ticket_order o LEFT JOIN showtime s ON s.id = o.showtime_id WHERE s.id IS NULL LIMIT 1)
       OR EXISTS (SELECT 1 FROM user_coupon uc LEFT JOIN `user` u ON u.id = uc.user_id WHERE u.id IS NULL LIMIT 1)
       OR EXISTS (SELECT 1 FROM comment cm LEFT JOIN movie m ON m.id = cm.movie_id WHERE m.id IS NULL LIMIT 1)
       OR EXISTS (SELECT 1 FROM comment cm LEFT JOIN `user` u ON u.id = cm.user_id WHERE u.id IS NULL LIMIT 1)
       OR EXISTS (SELECT 1 FROM comment cm LEFT JOIN ticket_order o ON o.id = cm.order_id WHERE o.id IS NULL LIMIT 1)
       OR EXISTS (SELECT 1 FROM user_movie_behavior b LEFT JOIN `user` u ON u.id = b.user_id WHERE u.id IS NULL LIMIT 1)
       OR EXISTS (SELECT 1 FROM user_movie_behavior b LEFT JOIN movie m ON m.id = b.movie_id WHERE m.id IS NULL LIMIT 1)
       OR EXISTS (SELECT 1 FROM ticket_order o LEFT JOIN user_coupon uc ON uc.id = o.coupon_id WHERE o.coupon_id IS NOT NULL AND uc.id IS NULL LIMIT 1)
       OR EXISTS (SELECT 1 FROM user_coupon uc LEFT JOIN ticket_order o ON o.id = uc.order_id WHERE uc.order_id IS NOT NULL AND o.id IS NULL LIMIT 1)
    THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V8 aborted: orphan rows must be repaired before foreign keys are added';
    END IF;

    IF EXISTS (SELECT 1 FROM `user` WHERE gender NOT IN (0, 1, 2) OR status NOT IN (0, 1) OR deleted NOT IN (0, 1) LIMIT 1)
       OR EXISTS (SELECT 1 FROM cinema WHERE status NOT IN (0, 1) OR deleted NOT IN (0, 1) LIMIT 1)
       OR EXISTS (SELECT 1 FROM hall WHERE total_rows <= 0 OR total_cols <= 0 OR total_seats <= 0 OR status NOT IN (0, 1) OR deleted NOT IN (0, 1) LIMIT 1)
       OR EXISTS (SELECT 1 FROM movie WHERE duration <= 0 OR (rating IS NOT NULL AND (rating < 0 OR rating > 10)) OR status NOT IN (0, 1, 2) OR deleted NOT IN (0, 1) LIMIT 1)
       OR EXISTS (SELECT 1 FROM showtime WHERE start_time >= end_time OR base_price < 0 OR total_seats <= 0 OR available_seats < 0 OR available_seats > total_seats OR status NOT IN (0, 1, 2) OR deleted NOT IN (0, 1) LIMIT 1)
       OR EXISTS (SELECT 1 FROM ticket_order WHERE seat_count <= 0 OR total_price < 0 OR original_price < 0 OR discount_amount < 0 OR discount_amount > original_price OR total_price > original_price OR status NOT IN (0, 1, 2, 3) OR is_picked NOT IN (0, 1) LIMIT 1)
       OR EXISTS (SELECT 1 FROM user_coupon WHERE amount < 0 OR min_spend < 0 OR valid_from > expire_time OR status NOT IN (0, 1, 2) LIMIT 1)
       OR EXISTS (SELECT 1 FROM comment WHERE rating NOT BETWEEN 1 AND 5 OR deleted NOT IN (0, 1) LIMIT 1)
       OR EXISTS (SELECT 1 FROM user_movie_behavior WHERE behavior_type NOT IN ('VIEW', 'FAVORITE') OR behavior_count <= 0 OR active NOT IN (0, 1) LIMIT 1)
    THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V8 aborted: invalid domain values must be repaired before CHECK constraints are added';
    END IF;
END$$
DELIMITER ;

CALL `validate_movie_ticket_integrity`();
DROP PROCEDURE `validate_movie_ticket_integrity`;

ALTER TABLE `hall`
  ADD CONSTRAINT `fk_hall_cinema` FOREIGN KEY (`cinema_id`) REFERENCES `cinema` (`id`) ON DELETE RESTRICT;

ALTER TABLE `showtime`
  ADD CONSTRAINT `fk_showtime_movie` FOREIGN KEY (`movie_id`) REFERENCES `movie` (`id`) ON DELETE RESTRICT,
  ADD CONSTRAINT `fk_showtime_cinema` FOREIGN KEY (`cinema_id`) REFERENCES `cinema` (`id`) ON DELETE RESTRICT,
  ADD CONSTRAINT `fk_showtime_hall` FOREIGN KEY (`hall_id`) REFERENCES `hall` (`id`) ON DELETE RESTRICT;

ALTER TABLE `ticket_order`
  ADD CONSTRAINT `fk_order_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  ADD CONSTRAINT `fk_order_showtime` FOREIGN KEY (`showtime_id`) REFERENCES `showtime` (`id`) ON DELETE RESTRICT;

ALTER TABLE `user_coupon`
  ADD CONSTRAINT `fk_coupon_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  ADD CONSTRAINT `fk_coupon_order` FOREIGN KEY (`order_id`) REFERENCES `ticket_order` (`id`) ON DELETE SET NULL;

ALTER TABLE `ticket_order`
  ADD CONSTRAINT `fk_order_coupon` FOREIGN KEY (`coupon_id`) REFERENCES `user_coupon` (`id`) ON DELETE SET NULL;

ALTER TABLE `comment`
  ADD CONSTRAINT `fk_comment_movie` FOREIGN KEY (`movie_id`) REFERENCES `movie` (`id`) ON DELETE RESTRICT,
  ADD CONSTRAINT `fk_comment_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  ADD CONSTRAINT `fk_comment_order` FOREIGN KEY (`order_id`) REFERENCES `ticket_order` (`id`) ON DELETE RESTRICT;

ALTER TABLE `user_movie_behavior`
  ADD CONSTRAINT `fk_behavior_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
  ADD CONSTRAINT `fk_behavior_movie` FOREIGN KEY (`movie_id`) REFERENCES `movie` (`id`) ON DELETE RESTRICT;

ALTER TABLE `user`
  ADD CONSTRAINT `ck_user_gender` CHECK (`gender` IN (0, 1, 2)),
  ADD CONSTRAINT `ck_user_role` CHECK (`role` IN ('USER', 'CINEMA_ADMIN', 'ADMIN')),
  ADD CONSTRAINT `ck_user_status` CHECK (`status` IN (0, 1)),
  ADD CONSTRAINT `ck_user_deleted` CHECK (`deleted` IN (0, 1));

ALTER TABLE `cinema`
  ADD CONSTRAINT `ck_cinema_status` CHECK (`status` IN (0, 1)),
  ADD CONSTRAINT `ck_cinema_deleted` CHECK (`deleted` IN (0, 1));

ALTER TABLE `hall`
  ADD CONSTRAINT `ck_hall_dimensions` CHECK (`total_rows` > 0 AND `total_cols` > 0 AND `total_seats` > 0),
  ADD CONSTRAINT `ck_hall_status` CHECK (`status` IN (0, 1)),
  ADD CONSTRAINT `ck_hall_deleted` CHECK (`deleted` IN (0, 1));

ALTER TABLE `movie`
  ADD CONSTRAINT `ck_movie_duration` CHECK (`duration` > 0),
  ADD CONSTRAINT `ck_movie_rating` CHECK (`rating` IS NULL OR (`rating` >= 0 AND `rating` <= 10)),
  ADD CONSTRAINT `ck_movie_status` CHECK (`status` IN (0, 1, 2)),
  ADD CONSTRAINT `ck_movie_deleted` CHECK (`deleted` IN (0, 1));

ALTER TABLE `showtime`
  ADD CONSTRAINT `ck_showtime_time` CHECK (`start_time` < `end_time`),
  ADD CONSTRAINT `ck_showtime_price` CHECK (`base_price` >= 0),
  ADD CONSTRAINT `ck_showtime_seats` CHECK (`total_seats` > 0 AND `available_seats` >= 0 AND `available_seats` <= `total_seats`),
  ADD CONSTRAINT `ck_showtime_status` CHECK (`status` IN (0, 1, 2)),
  ADD CONSTRAINT `ck_showtime_deleted` CHECK (`deleted` IN (0, 1));

ALTER TABLE `ticket_order`
  ADD CONSTRAINT `ck_order_seat_count` CHECK (`seat_count` > 0),
  ADD CONSTRAINT `ck_order_amounts` CHECK (`total_price` >= 0 AND `original_price` >= 0 AND `discount_amount` >= 0 AND `discount_amount` <= `original_price` AND `total_price` <= `original_price`),
  ADD CONSTRAINT `ck_order_status` CHECK (`status` IN (0, 1, 2, 3)),
  ADD CONSTRAINT `ck_order_picked` CHECK (`is_picked` IN (0, 1));

ALTER TABLE `user_coupon`
  ADD CONSTRAINT `ck_coupon_amounts` CHECK (`amount` >= 0 AND `min_spend` >= 0),
  ADD CONSTRAINT `ck_coupon_dates` CHECK (`valid_from` <= `expire_time`),
  ADD CONSTRAINT `ck_coupon_status` CHECK (`status` IN (0, 1, 2));

ALTER TABLE `comment`
  ADD CONSTRAINT `ck_comment_rating` CHECK (`rating` BETWEEN 1 AND 5),
  ADD CONSTRAINT `ck_comment_deleted` CHECK (`deleted` IN (0, 1));

ALTER TABLE `user_movie_behavior`
  ADD CONSTRAINT `ck_behavior_type` CHECK (`behavior_type` IN ('VIEW', 'FAVORITE')),
  ADD CONSTRAINT `ck_behavior_count` CHECK (`behavior_count` > 0),
  ADD CONSTRAINT `ck_behavior_active` CHECK (`active` IN (0, 1));
