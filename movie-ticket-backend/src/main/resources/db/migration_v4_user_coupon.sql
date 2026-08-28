-- v4：用户优惠券与订单优惠信息
USE `movie_ticket`;

CREATE TABLE IF NOT EXISTS `user_coupon` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户优惠券ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `name` VARCHAR(100) NOT NULL COMMENT '优惠券名称',
  `amount` DECIMAL(10,2) NOT NULL COMMENT '优惠金额',
  `min_spend` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '最低消费金额',
  `status` INT NOT NULL DEFAULT 0 COMMENT '0-可用，1-已使用，2-已过期',
  `order_id` BIGINT DEFAULT NULL COMMENT '使用该券的订单ID',
  `valid_from` DATETIME NOT NULL COMMENT '生效时间',
  `expire_time` DATETIME NOT NULL COMMENT '过期时间',
  `use_time` DATETIME DEFAULT NULL COMMENT '使用时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_coupon_user_status` (`user_id`, `status`),
  KEY `idx_coupon_expire_time` (`expire_time`),
  KEY `idx_coupon_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户优惠券表';

SET @has_original_price = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket_order' AND COLUMN_NAME = 'original_price'
);
SET @sql_original_price = IF(
  @has_original_price = 0,
  'ALTER TABLE `ticket_order` ADD COLUMN `original_price` DECIMAL(10,2) NULL COMMENT ''优惠前金额'' AFTER `total_price`',
  'SELECT 1'
);
PREPARE stmt_original_price FROM @sql_original_price;
EXECUTE stmt_original_price;
DEALLOCATE PREPARE stmt_original_price;

SET @has_discount_amount = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket_order' AND COLUMN_NAME = 'discount_amount'
);
SET @sql_discount_amount = IF(
  @has_discount_amount = 0,
  'ALTER TABLE `ticket_order` ADD COLUMN `discount_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT ''优惠金额'' AFTER `original_price`',
  'SELECT 1'
);
PREPARE stmt_discount_amount FROM @sql_discount_amount;
EXECUTE stmt_discount_amount;
DEALLOCATE PREPARE stmt_discount_amount;

SET @has_coupon_id = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket_order' AND COLUMN_NAME = 'coupon_id'
);
SET @sql_coupon_id = IF(
  @has_coupon_id = 0,
  'ALTER TABLE `ticket_order` ADD COLUMN `coupon_id` BIGINT NULL COMMENT ''使用的用户优惠券ID'' AFTER `discount_amount`',
  'SELECT 1'
);
PREPARE stmt_coupon_id FROM @sql_coupon_id;
EXECUTE stmt_coupon_id;
DEALLOCATE PREPARE stmt_coupon_id;

UPDATE `ticket_order`
SET `original_price` = `total_price`
WHERE `original_price` IS NULL;

ALTER TABLE `ticket_order`
  MODIFY COLUMN `original_price` DECIMAL(10,2) NOT NULL COMMENT '优惠前金额';
