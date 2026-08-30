-- 智能电影购票系统：新建数据库的唯一主结构脚本
-- 兼容 MySQL 8.0+ / 9.x；已有数据库请执行 migration_v3_schema_alignment.sql

CREATE DATABASE IF NOT EXISTS `movie_ticket`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `movie_ticket`;

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `comment`;
DROP TABLE IF EXISTS `user_movie_behavior`;
DROP TABLE IF EXISTS `user_coupon`;
DROP TABLE IF EXISTS `ticket_order`;
DROP TABLE IF EXISTS `showtime`;
DROP TABLE IF EXISTS `movie`;
DROP TABLE IF EXISTS `hall`;
DROP TABLE IF EXISTS `cinema`;
DROP TABLE IF EXISTS `user`;

CREATE TABLE `user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码',
  `nickname` VARCHAR(50) DEFAULT NULL COMMENT '昵称',
  `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像',
  `favorite_genres` JSON DEFAULT NULL COMMENT '喜欢的电影类型 JSON 数组',
  `role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT 'USER/CINEMA_ADMIN/ADMIN',
  `status` INT NOT NULL DEFAULT 1 COMMENT '0-禁用，1-启用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_phone` (`phone`),
  KEY `idx_email` (`email`),
  KEY `idx_role_status` (`role`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE `cinema` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '影院ID',
  `name` VARCHAR(100) NOT NULL COMMENT '影院名称',
  `address` VARCHAR(255) NOT NULL COMMENT '地址',
  `lng` DECIMAL(10,7) DEFAULT NULL COMMENT '经度',
  `lat` DECIMAL(10,7) DEFAULT NULL COMMENT '纬度',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
  `description` TEXT COMMENT '简介',
  `images` TEXT COMMENT '图片，JSON 数组或逗号分隔 URL',
  `status` INT NOT NULL DEFAULT 1 COMMENT '0-停业，1-营业',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_name` (`name`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='影院表';

CREATE TABLE `hall` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '影厅ID',
  `cinema_id` BIGINT NOT NULL COMMENT '影院ID',
  `name` VARCHAR(50) NOT NULL COMMENT '影厅名称',
  `total_rows` INT NOT NULL COMMENT '总行数',
  `total_cols` INT NOT NULL COMMENT '总列数',
  `total_seats` INT NOT NULL COMMENT '总座位数',
  `seat_layout` TEXT DEFAULT NULL COMMENT '座位布局 JSON',
  `status` INT NOT NULL DEFAULT 1 COMMENT '0-停用，1-启用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_cinema_id` (`cinema_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='影厅表';

CREATE TABLE `movie` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '电影ID',
  `title` VARCHAR(100) NOT NULL COMMENT '电影名称',
  `english_title` VARCHAR(100) DEFAULT NULL COMMENT '英文名称',
  `director` VARCHAR(100) DEFAULT NULL COMMENT '导演',
  `actors` VARCHAR(255) DEFAULT NULL COMMENT '主演',
  `genre` VARCHAR(50) DEFAULT NULL COMMENT '类型',
  `country` VARCHAR(50) DEFAULT NULL COMMENT '国家/地区',
  `language` VARCHAR(50) DEFAULT NULL COMMENT '语言',
  `duration` INT NOT NULL COMMENT '时长（分钟）',
  `release_date` DATE DEFAULT NULL COMMENT '上映日期',
  `poster` VARCHAR(255) DEFAULT NULL COMMENT '海报',
  `trailer` VARCHAR(255) DEFAULT NULL COMMENT '预告片',
  `description` TEXT COMMENT '简介',
  `rating` DECIMAL(3,1) DEFAULT NULL COMMENT '评分（10 分制）',
  `status` INT NOT NULL DEFAULT 1 COMMENT '0-下架，1-上映中，2-即将上映',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_title` (`title`),
  KEY `idx_status` (`status`),
  KEY `idx_release_date` (`release_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='电影表';

CREATE TABLE `showtime` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '场次ID',
  `movie_id` BIGINT NOT NULL COMMENT '电影ID',
  `cinema_id` BIGINT NOT NULL COMMENT '影院ID',
  `hall_id` BIGINT NOT NULL COMMENT '影厅ID',
  `start_time` DATETIME NOT NULL COMMENT '开始时间',
  `end_time` DATETIME NOT NULL COMMENT '结束时间',
  `base_price` DECIMAL(10,2) NOT NULL COMMENT '基础票价',
  `total_seats` INT NOT NULL COMMENT '总座位数',
  `available_seats` INT NOT NULL COMMENT '可用座位数',
  `status` INT NOT NULL DEFAULT 0 COMMENT '0-可售，1-已结束，2-已取消',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_movie_id` (`movie_id`),
  KEY `idx_cinema_id` (`cinema_id`),
  KEY `idx_hall_id` (`hall_id`),
  KEY `idx_start_time` (`start_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='场次表';

CREATE TABLE `ticket_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_no` VARCHAR(50) NOT NULL COMMENT '订单号',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `showtime_id` BIGINT NOT NULL COMMENT '场次ID',
  `seats` VARCHAR(255) NOT NULL COMMENT '座位，多个用逗号分隔',
  `seat_count` INT NOT NULL COMMENT '座位数量',
  `total_price` DECIMAL(10,2) NOT NULL COMMENT '总价',
  `original_price` DECIMAL(10,2) NOT NULL COMMENT '优惠前金额',
  `discount_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '优惠金额',
  `coupon_id` BIGINT DEFAULT NULL COMMENT '使用的用户优惠券ID',
  `status` INT NOT NULL DEFAULT 0 COMMENT '0-待支付，1-已支付，2-已取消，3-已退款',
  `ticket_code` VARCHAR(8) DEFAULT NULL COMMENT '8 位取票码',
  `is_picked` INT NOT NULL DEFAULT 0 COMMENT '0-未取票，1-已取票',
  `pick_time` DATETIME DEFAULT NULL COMMENT '取票时间',
  `pay_time` DATETIME DEFAULT NULL COMMENT '支付时间',
  `cancel_time` DATETIME DEFAULT NULL COMMENT '取消或退款时间',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_ticket_code` (`ticket_code`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_showtime_id` (`showtime_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

CREATE TABLE `user_coupon` (
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

CREATE TABLE `comment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '评价ID',
  `movie_id` BIGINT NOT NULL COMMENT '电影ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `order_id` BIGINT NOT NULL COMMENT '关联订单ID（观影凭据）',
  `rating` INT NOT NULL COMMENT '1-5 星',
  `content` VARCHAR(500) DEFAULT NULL COMMENT '评价内容',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` INT NOT NULL DEFAULT 0 COMMENT '0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_movie` (`user_id`, `movie_id`),
  KEY `idx_movie_id` (`movie_id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='电影评价表';

CREATE TABLE `user_movie_behavior` (
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

SET FOREIGN_KEY_CHECKS = 1;
