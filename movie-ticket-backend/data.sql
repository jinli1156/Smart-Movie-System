-- 兼容入口：请从 movie-ticket-backend 目录使用 MySQL 客户端执行本文件。
-- 唯一种子数据脚本位于 src/main/resources/db/data.sql。
-- 先启动后端完成 Flyway V24，再停止后端后执行；会重置全部演示业务数据。
-- 排片按北京时间覆盖执行当天至未来第 10 天，影院管理员已绑定影院 1。
SOURCE src/main/resources/db/data.sql;
