-- 将 v6 基线接管的旧库与新建库对齐；不重置业务记录。
-- 先完成数据预检，再执行会隐式提交的 DDL，避免在不合格数据上部分升级。
DROP PROCEDURE IF EXISTS `align_legacy_movie_ticket_schema`;
DELIMITER $$
CREATE PROCEDURE `align_legacy_movie_ticket_schema`()
BEGIN
  IF EXISTS (SELECT 1 FROM `user` WHERE role IS NULL OR status IS NULL OR create_time IS NULL OR update_time IS NULL)
     OR EXISTS (SELECT 1 FROM cinema WHERE status IS NULL OR create_time IS NULL OR update_time IS NULL)
     OR EXISTS (SELECT 1 FROM hall WHERE status IS NULL OR create_time IS NULL OR update_time IS NULL)
     OR EXISTS (SELECT 1 FROM movie WHERE status IS NULL OR create_time IS NULL OR update_time IS NULL)
     OR EXISTS (SELECT 1 FROM showtime WHERE create_time IS NULL OR update_time IS NULL)
     OR EXISTS (SELECT 1 FROM ticket_order WHERE status IS NULL OR create_time IS NULL OR update_time IS NULL)
  THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V24 aborted: repair NULL roles, statuses or timestamps before migration';
  END IF;

  IF EXISTS (SELECT username COLLATE utf8mb4_unicode_ci FROM `user`
             GROUP BY username COLLATE utf8mb4_unicode_ci HAVING COUNT(*) > 1)
     OR EXISTS (SELECT order_no COLLATE utf8mb4_unicode_ci FROM ticket_order
                GROUP BY order_no COLLATE utf8mb4_unicode_ci HAVING COUNT(*) > 1)
     OR EXISTS (SELECT ticket_code COLLATE utf8mb4_unicode_ci FROM ticket_order
                WHERE ticket_code IS NOT NULL
                GROUP BY ticket_code COLLATE utf8mb4_unicode_ci HAVING COUNT(*) > 1)
  THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'V24 aborted: duplicate username, order number or ticket code under target collation';
  END IF;

  ALTER TABLE `user` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    MODIFY role VARCHAR(20) NOT NULL DEFAULT 'USER',
    MODIFY status INT NOT NULL DEFAULT 1,
    MODIFY create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    MODIFY update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
  ALTER TABLE cinema CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    MODIFY status INT NOT NULL DEFAULT 1,
    MODIFY create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    MODIFY update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
  ALTER TABLE hall CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    MODIFY status INT NOT NULL DEFAULT 1,
    MODIFY create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    MODIFY update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
  ALTER TABLE movie CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    MODIFY status INT NOT NULL DEFAULT 1,
    MODIFY create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    MODIFY update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
  ALTER TABLE showtime CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    MODIFY create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    MODIFY update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
  ALTER TABLE ticket_order CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    MODIFY status INT NOT NULL DEFAULT 0,
    MODIFY create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    MODIFY update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
  ALTER TABLE `comment` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND INDEX_NAME = 'idx_role_status') THEN
    ALTER TABLE `user` ADD INDEX idx_role_status (role, status);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket_order' AND INDEX_NAME = 'uk_ticket_code') THEN
    ALTER TABLE ticket_order ADD UNIQUE INDEX uk_ticket_code (ticket_code);
  END IF;
  -- 早期的普通取票码索引由唯一索引替代。seat_count 已是代码使用的字段，
  -- 不删除旧库额外的 ticket_count，避免破坏尚未确认的外部依赖。
  IF EXISTS (SELECT 1 FROM information_schema.STATISTICS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ticket_order' AND INDEX_NAME = 'idx_ticket_code') THEN
    ALTER TABLE ticket_order DROP INDEX idx_ticket_code;
  END IF;
END$$
DELIMITER ;
CALL `align_legacy_movie_ticket_schema`();
DROP PROCEDURE `align_legacy_movie_ticket_schema`;
