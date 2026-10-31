-- Profile fields introduced by the user profile page.
ALTER TABLE `user`
  ADD COLUMN `gender` TINYINT NOT NULL DEFAULT 0 AFTER `avatar`,
  ADD COLUMN `birthday` DATE NULL AFTER `gender`,
  ADD COLUMN `city` VARCHAR(50) NULL AFTER `birthday`,
  ADD COLUMN `address` VARCHAR(255) NULL AFTER `city`,
  ADD COLUMN `signature` VARCHAR(50) NULL AFTER `favorite_genres`;

-- Logical deletion keeps historical orders and reporting relationships intact.
ALTER TABLE `user`
  ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0 AFTER `status`;
ALTER TABLE `cinema`
  ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0 AFTER `status`;
ALTER TABLE `hall`
  ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0 AFTER `status`;
ALTER TABLE `movie`
  ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0 AFTER `status`;
ALTER TABLE `showtime`
  ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0 AFTER `status`;

-- Indexes used by timeout scanning, revenue aggregation and joined list filters.
ALTER TABLE `user`
  ADD KEY `idx_user_deleted_status` (`deleted`, `status`);
ALTER TABLE `cinema`
  ADD KEY `idx_cinema_deleted_status` (`deleted`, `status`);
ALTER TABLE `hall`
  ADD KEY `idx_hall_deleted_status` (`deleted`, `status`);
ALTER TABLE `movie`
  ADD KEY `idx_movie_deleted_status` (`deleted`, `status`);
ALTER TABLE `showtime`
  ADD KEY `idx_showtime_sale_query` (`deleted`, `movie_id`, `cinema_id`, `status`, `start_time`);
ALTER TABLE `ticket_order`
  ADD KEY `idx_order_status_created` (`status`, `create_time`),
  ADD KEY `idx_order_status_paid` (`status`, `pay_time`),
  ADD KEY `idx_order_user_status_created` (`user_id`, `status`, `create_time`);
