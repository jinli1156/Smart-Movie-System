-- ============================================================
-- v3：将历史数据库/当前 Navicat 导出结构与 Java 实体统一
-- 执行前请备份数据库；脚本不会删除业务订单、用户或评价数据
-- ============================================================

USE `movie_ticket`;

DROP PROCEDURE IF EXISTS `migrate_movie_ticket_v3`;

DELIMITER $$

CREATE PROCEDURE `migrate_movie_ticket_v3`()
BEGIN
    DECLARE duplicate_ticket_codes INT DEFAULT 0;

    -- 当前数据库包含 hall.total_seats，后端现在也会在新增/编辑时维护该字段。
    ALTER TABLE `hall`
        ADD COLUMN IF NOT EXISTS `total_seats` INT NULL COMMENT '总座位数' AFTER `total_cols`;

    UPDATE `hall`
    SET `total_seats` = `total_rows` * `total_cols`
    WHERE `total_seats` IS NULL
       OR `total_seats` <> `total_rows` * `total_cols`;

    ALTER TABLE `hall`
        MODIFY COLUMN `total_seats` INT NOT NULL COMMENT '总座位数';

    -- 订单实体实际使用 seat_count；旧 schema 使用 ticket_count，当前库两列并存。
    ALTER TABLE `ticket_order`
        ADD COLUMN IF NOT EXISTS `seat_count` INT NULL COMMENT '座位数量' AFTER `seats`,
        ADD COLUMN IF NOT EXISTS `ticket_code` VARCHAR(8) NULL COMMENT '8位取票码' AFTER `status`,
        ADD COLUMN IF NOT EXISTS `is_picked` INT NOT NULL DEFAULT 0 COMMENT '0-未取票，1-已取票' AFTER `ticket_code`,
        ADD COLUMN IF NOT EXISTS `pick_time` DATETIME NULL COMMENT '取票时间' AFTER `is_picked`,
        ADD COLUMN IF NOT EXISTS `cancel_time` DATETIME NULL COMMENT '取消或退款时间' AFTER `pay_time`,
        ADD COLUMN IF NOT EXISTS `remark` VARCHAR(500) NULL COMMENT '备注' AFTER `cancel_time`;

    IF EXISTS (
        SELECT 1
        FROM `information_schema`.`columns`
        WHERE `table_schema` = DATABASE()
          AND `table_name` = 'ticket_order'
          AND `column_name` = 'ticket_count'
    ) THEN
        UPDATE `ticket_order`
        SET `seat_count` = COALESCE(`seat_count`, `ticket_count`)
        WHERE `seat_count` IS NULL;

        ALTER TABLE `ticket_order` DROP COLUMN `ticket_count`;
    END IF;

    -- 最后兜底按逗号分隔的座位文本计算票数。
    UPDATE `ticket_order`
    SET `seat_count` = CASE
        WHEN `seats` IS NULL OR TRIM(`seats`) = '' THEN 0
        ELSE 1 + CHAR_LENGTH(`seats`) - CHAR_LENGTH(REPLACE(`seats`, ',', ''))
    END
    WHERE `seat_count` IS NULL OR `seat_count` <= 0;

    ALTER TABLE `ticket_order`
        MODIFY COLUMN `seat_count` INT NOT NULL COMMENT '座位数量',
        MODIFY COLUMN `status` INT NOT NULL DEFAULT 0 COMMENT '0-待支付，1-已支付，2-已取消，3-已退款',
        MODIFY COLUMN `is_picked` INT NOT NULL DEFAULT 0 COMMENT '0-未取票，1-已取票';

    SELECT COUNT(*) INTO duplicate_ticket_codes
    FROM (
        SELECT `ticket_code`
        FROM `ticket_order`
        WHERE `ticket_code` IS NOT NULL AND `ticket_code` <> ''
        GROUP BY `ticket_code`
        HAVING COUNT(*) > 1
    ) AS duplicate_codes;

    IF duplicate_ticket_codes > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '存在重复 ticket_code，请先处理后再执行 v3 迁移';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM `information_schema`.`statistics`
        WHERE `table_schema` = DATABASE()
          AND `table_name` = 'ticket_order'
          AND `index_name` = 'idx_ticket_code'
    ) THEN
        ALTER TABLE `ticket_order` DROP INDEX `idx_ticket_code`;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM `information_schema`.`statistics`
        WHERE `table_schema` = DATABASE()
          AND `table_name` = 'ticket_order'
          AND `index_name` = 'uk_ticket_code'
    ) THEN
        ALTER TABLE `ticket_order`
            ADD UNIQUE KEY `uk_ticket_code` (`ticket_code`);
    END IF;
END$$

DELIMITER ;

CALL `migrate_movie_ticket_v3`();
DROP PROCEDURE `migrate_movie_ticket_v3`;

-- 迁移结果检查
SELECT `column_name`, `column_type`, `is_nullable`, `column_default`
FROM `information_schema`.`columns`
WHERE `table_schema` = DATABASE()
  AND `table_name` = 'ticket_order'
ORDER BY `ordinal_position`;

SELECT `index_name`, `non_unique`, `column_name`
FROM `information_schema`.`statistics`
WHERE `table_schema` = DATABASE()
  AND `table_name` = 'ticket_order'
ORDER BY `index_name`, `seq_in_index`;
