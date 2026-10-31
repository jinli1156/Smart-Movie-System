-- 钱包模块已下线，保留历史迁移并在新版本中移除字段。
ALTER TABLE `user`
    DROP COLUMN `wallet_balance`;

CREATE TABLE IF NOT EXISTS `coupon_code` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(32) NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `amount` DECIMAL(10,2) NOT NULL,
  `min_spend` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  `valid_days` INT NOT NULL DEFAULT 7,
  `max_redemptions` INT NOT NULL DEFAULT 1,
  `redeemed_count` INT NOT NULL DEFAULT 0,
  `valid_from` DATETIME NOT NULL,
  `expire_time` DATETIME NOT NULL,
  `status` TINYINT NOT NULL DEFAULT 1,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_coupon_code` (`code`),
  KEY `idx_coupon_code_status_time` (`status`, `valid_from`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `coupon_code_redemption` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `coupon_code_id` BIGINT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `user_coupon_id` BIGINT NOT NULL,
  `redeem_time` DATETIME NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code_user` (`coupon_code_id`, `user_id`),
  UNIQUE KEY `uk_redemption_user_coupon` (`user_coupon_id`),
  KEY `idx_redemption_user_time` (`user_id`, `redeem_time`),
  CONSTRAINT `fk_redemption_code` FOREIGN KEY (`coupon_code_id`) REFERENCES `coupon_code` (`id`),
  CONSTRAINT `fk_redemption_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`),
  CONSTRAINT `fk_redemption_user_coupon` FOREIGN KEY (`user_coupon_id`) REFERENCES `user_coupon` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 可直接用于验收兑换流程的公共演示码；每个账号限兑一次。
INSERT INTO `coupon_code`
  (`code`, `name`, `amount`, `min_spend`, `valid_days`, `max_redemptions`, `redeemed_count`, `valid_from`, `expire_time`, `status`)
VALUES
  ('JINLI699', '兑换码专享满 80 减 6.99 元券', 6.99, 80.00, 7, 10000, 0, '2026-01-01 00:00:00', '2030-12-31 23:59:59', 1);
