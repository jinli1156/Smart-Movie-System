-- ============================================================
-- 统一迁移脚本 v2（合并所有历史 migration）
-- 执行日期：2026-06-02
-- 适用：已有库升级，所有语句幂等可重复执行
-- 新建库请直接用 schema.sql + data.sql
-- ============================================================

USE movie_ticket;

-- 1. 影院表：添加经纬度列
ALTER TABLE `cinema` ADD COLUMN IF NOT EXISTS `lng` DECIMAL(10,7) COMMENT '经度' AFTER `address`;
ALTER TABLE `cinema` ADD COLUMN IF NOT EXISTS `lat` DECIMAL(10,7) COMMENT '纬度' AFTER `lng`;

-- 2. 影院表：回填种子影院经纬度（南宁，仅在坐标为空时更新）
UPDATE `cinema` SET `lng` = 108.3100000, `lat` = 22.8350000
WHERE `name` = '万达影城(朝阳店)' AND (`lng` IS NULL OR `lat` IS NULL);

UPDATE `cinema` SET `lng` = 108.3200000, `lat` = 22.8180000
WHERE `name` = 'CGV影城(悦荟店)' AND (`lng` IS NULL OR `lat` IS NULL);

UPDATE `cinema` SET `lng` = 108.3185000, `lat` = 22.8178000
WHERE `name` = '博纳国际影城(悦荟店)' AND (`lng` IS NULL OR `lat` IS NULL);

UPDATE `cinema` SET `lng` = 108.2730000, `lat` = 22.8420000
WHERE `name` = '耀莱成龙国际影城(五棵松店)' AND (`lng` IS NULL OR `lat` IS NULL);

UPDATE `cinema` SET `lng` = 108.2950000, `lat` = 22.8060000
WHERE `name` = 'UME国际影城(双井店)' AND (`lng` IS NULL OR `lat` IS NULL);

-- 3. 订单表：添加取票相关字段
ALTER TABLE `ticket_order` ADD COLUMN IF NOT EXISTS `ticket_code` VARCHAR(8) COMMENT '取票码（8位数字）' AFTER `status`;
ALTER TABLE `ticket_order` ADD COLUMN IF NOT EXISTS `is_picked` INT NOT NULL DEFAULT 0 COMMENT '是否已取票：0-未取票，1-已取票' AFTER `ticket_code`;
ALTER TABLE `ticket_order` ADD COLUMN IF NOT EXISTS `pick_time` DATETIME COMMENT '取票时间' AFTER `is_picked`;

-- 4. 评价表：创建（如不存在）
CREATE TABLE IF NOT EXISTS `comment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '评价ID',
  `movie_id` BIGINT NOT NULL COMMENT '电影ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `order_id` BIGINT NOT NULL COMMENT '关联订单ID（证明已观影）',
  `rating` INT NOT NULL COMMENT '评分：1-5 星',
  `content` VARCHAR(500) COMMENT '评价内容',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` INT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_movie` (`user_id`, `movie_id`),
  KEY `idx_movie_id` (`movie_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电影评价表';

-- 5. 场次表：状态编码修正（旧 0-停售→2-已取消，旧 1-售票中→按 end_time 分 0/1）
UPDATE `showtime`
SET `status` = CASE
    WHEN `status` = 2 THEN 2
    WHEN `status` = 1 AND `end_time` < NOW() THEN 1
    WHEN `status` = 1 AND `end_time` >= NOW() THEN 0
    WHEN `status` = 0 THEN 2
    ELSE `status`
END
WHERE `status` IN (0, 1);

ALTER TABLE `showtime`
    MODIFY COLUMN `status` INT NOT NULL DEFAULT 0
    COMMENT '状态：0-可售，1-已结束，2-已取消';

-- 6. 电影表：修复历史海报路径（裸文件名补 /api/uploads/ 前缀）
UPDATE `movie`
SET `poster` = CONCAT('/api/uploads/', `poster`)
WHERE `poster` IS NOT NULL
  AND `poster` != ''
  AND `poster` NOT LIKE '/%'
  AND `poster` NOT LIKE 'http%';

-- 验证
SELECT 'cinema' AS tbl, COUNT(*) AS cnt FROM `cinema` WHERE `lng` IS NOT NULL
UNION ALL
SELECT 'comment', COUNT(*) FROM `comment`
UNION ALL
SELECT 'ticket_order(pickup)', COUNT(*) FROM `ticket_order` WHERE `ticket_code` IS NOT NULL;
