-- ============================================================
-- 2026-08-30 至 2026-09-30 功能测试排片
-- 可重复执行：相同影厅、相同时间或存在时间冲突时自动跳过
-- ============================================================

USE `movie_ticket`;

INSERT INTO `showtime` (
    `movie_id`, `cinema_id`, `hall_id`, `start_time`, `end_time`,
    `base_price`, `total_seats`, `available_seats`, `status`
)
WITH RECURSIVE `schedule_dates` AS (
    SELECT DATE('2026-08-30') AS `schedule_date`
    UNION ALL
    SELECT DATE_ADD(`schedule_date`, INTERVAL 1 DAY)
    FROM `schedule_dates`
    WHERE `schedule_date` < DATE('2026-09-30')
),
`plans` AS (
    SELECT '长安三万里' AS `movie_title`, '万达影城(朝阳店)' AS `cinema_name`,
           '1号厅' AS `hall_name`, TIME('16:00:00') AS `start_at`, 50.00 AS `price`
    UNION ALL
    SELECT '长安三万里', '万达影城(朝阳店)', '1号厅', TIME('20:00:00'), 50.00
    UNION ALL
    SELECT '满江红', 'UME国际影城(双井店)', '1号厅', TIME('15:00:00'), 48.00
    UNION ALL
    SELECT '满江红', 'UME国际影城(双井店)', '1号厅', TIME('19:30:00'), 48.00
    UNION ALL
    SELECT '封神第二部：战火西岐', 'CGV影城(三里屯店)', '1号厅', TIME('00:00:00'), 50.00
    UNION ALL
    SELECT '封神第二部：战火西岐', 'CGV影城(三里屯店)', '1号厅', TIME('19:00:00'), 50.00
    UNION ALL
    SELECT '沙丘3', '万达影城(朝阳店)', 'VIP厅', TIME('12:50:00'), 50.00
    UNION ALL
    SELECT '沙丘3', '万达影城(朝阳店)', 'VIP厅', TIME('19:00:00'), 50.00
),
`resolved_plans` AS (
    SELECT
        `m`.`id` AS `movie_id`,
        `c`.`id` AS `cinema_id`,
        `h`.`id` AS `hall_id`,
        TIMESTAMP(`d`.`schedule_date`, `p`.`start_at`) AS `start_time`,
        DATE_ADD(
            TIMESTAMP(`d`.`schedule_date`, `p`.`start_at`),
            INTERVAL `m`.`duration` MINUTE
        ) AS `end_time`,
        `p`.`price`,
        `h`.`total_seats`
    FROM `schedule_dates` `d`
    CROSS JOIN `plans` `p`
    INNER JOIN `movie` `m` ON `m`.`title` = `p`.`movie_title`
    INNER JOIN `cinema` `c` ON `c`.`name` = `p`.`cinema_name`
    INNER JOIN `hall` `h`
        ON `h`.`cinema_id` = `c`.`id`
       AND `h`.`name` = `p`.`hall_name`
)
SELECT
    `p`.`movie_id`,
    `p`.`cinema_id`,
    `p`.`hall_id`,
    `p`.`start_time`,
    `p`.`end_time`,
    `p`.`price`,
    `p`.`total_seats`,
    `p`.`total_seats`,
    0
FROM `resolved_plans` `p`
WHERE NOT EXISTS (
    SELECT 1
    FROM `showtime` `existing`
    WHERE `existing`.`hall_id` = `p`.`hall_id`
      AND `existing`.`status` <> 2
      AND `existing`.`start_time` < `p`.`end_time`
      AND `existing`.`end_time` > `p`.`start_time`
);

SELECT
    `m`.`title` AS `movie_title`,
    COUNT(*) AS `showtime_count`,
    MIN(`s`.`start_time`) AS `first_showtime`,
    MAX(`s`.`start_time`) AS `last_showtime`
FROM `showtime` `s`
INNER JOIN `movie` `m` ON `m`.`id` = `s`.`movie_id`
WHERE `m`.`title` IN ('长安三万里', '满江红', '封神第二部：战火西岐', '沙丘3')
  AND `s`.`start_time` >= '2026-08-30 00:00:00'
  AND `s`.`start_time` < '2026-10-01 00:00:00'
GROUP BY `m`.`id`, `m`.`title`
ORDER BY `m`.`id`;
