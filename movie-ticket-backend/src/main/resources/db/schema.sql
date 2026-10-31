-- 数据库引导脚本：只创建空数据库，不再直接维护业务表结构。
-- 启动后端后，Flyway 会按 V1、V7、V8... 的顺序创建或升级表、索引与约束。
-- 已有数据库不要重建；备份后直接启动后端，由 Flyway 从版本 6 基线升级。

CREATE DATABASE IF NOT EXISTS `movie_ticket`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
