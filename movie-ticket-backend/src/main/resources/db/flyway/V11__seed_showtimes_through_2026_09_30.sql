-- 为全部营业影院补齐 2026-09-05 至 2026-09-30 的排片。
-- 常规场次间隔预留至少 20 分钟清场时间；脚本可重复执行而不会制造重复或冲突场次。

DROP TEMPORARY TABLE IF EXISTS `tmp_schedule_dates`;
CREATE TEMPORARY TABLE `tmp_schedule_dates` AS
WITH RECURSIVE `date_series` (`show_date`) AS (
  SELECT DATE('2026-09-05')
  UNION ALL
  SELECT DATE_ADD(`show_date`, INTERVAL 1 DAY)
  FROM `date_series`
  WHERE `show_date` < DATE('2026-09-30')
)
SELECT `show_date` FROM `date_series`;

DROP TEMPORARY TABLE IF EXISTS `tmp_schedule_movies`;
CREATE TEMPORARY TABLE `tmp_schedule_movies` AS
SELECT
  `eligible`.`show_date`,
  `eligible`.`movie_id`,
  `eligible`.`duration`,
  ROW_NUMBER() OVER (
    PARTITION BY `eligible`.`show_date`
    ORDER BY `eligible`.`release_date` DESC, `eligible`.`rating` DESC, `eligible`.`movie_id`
  ) AS `movie_rank`,
  COUNT(*) OVER (PARTITION BY `eligible`.`show_date`) AS `movie_count`
FROM (
  SELECT
    `d`.`show_date`,
    `m`.`id` AS `movie_id`,
    `m`.`duration`,
    `m`.`release_date`,
    COALESCE(`m`.`rating`, 0) AS `rating`
  FROM `tmp_schedule_dates` `d`
  JOIN `movie` `m`
    ON `m`.`deleted` = 0
   AND `m`.`status` = 1
   AND `m`.`release_date` BETWEEN DATE('2026-07-01') AND `d`.`show_date`
   AND `m`.`duration` BETWEEN 60 AND 210
) `eligible`;

-- 超长片只在每家影院容量最大的影厅安排周六上午专场。
INSERT INTO `showtime`
(`movie_id`, `cinema_id`, `hall_id`, `start_time`, `end_time`, `base_price`, `total_seats`, `available_seats`, `status`)
SELECT
  `long_movie`.`id`,
  `premium_hall`.`cinema_id`,
  `premium_hall`.`id`,
  TIMESTAMP(`d`.`show_date`, '09:30:00'),
  DATE_ADD(TIMESTAMP(`d`.`show_date`, '09:30:00'), INTERVAL `long_movie`.`duration` MINUTE),
  46.00
    + CASE
        WHEN `premium_hall`.`name` LIKE '%VIP%' THEN 12.00
        WHEN `premium_hall`.`name` LIKE '%IMAX%' OR `premium_hall`.`name` LIKE '%巨幕%' THEN 10.00
        WHEN `premium_hall`.`name` LIKE '%杜比%' OR `premium_hall`.`name` LIKE '%激光%' THEN 6.00
        ELSE 0.00
      END
    + MOD(`premium_hall`.`cinema_id`, 3) * 2.00,
  `premium_hall`.`total_seats`,
  `premium_hall`.`total_seats`,
  0
FROM `tmp_schedule_dates` `d`
JOIN (
  SELECT `ranked_hall`.*
  FROM (
    SELECT
      `h`.`id`, `h`.`cinema_id`, `h`.`name`, `h`.`total_seats`,
      ROW_NUMBER() OVER (
        PARTITION BY `h`.`cinema_id`
        ORDER BY `h`.`total_seats` DESC, `h`.`id`
      ) AS `hall_rank`
    FROM `hall` `h`
    JOIN `cinema` `c` ON `c`.`id` = `h`.`cinema_id`
    WHERE `h`.`deleted` = 0 AND `h`.`status` = 1
      AND `c`.`deleted` = 0 AND `c`.`status` = 1
  ) `ranked_hall`
  WHERE `ranked_hall`.`hall_rank` = 1
) `premium_hall`
JOIN `movie` `long_movie`
  ON `long_movie`.`title` = '杀死比尔：血色全传'
 AND `long_movie`.`deleted` = 0
 AND `long_movie`.`status` = 1
 AND `long_movie`.`release_date` <= `d`.`show_date`
WHERE DAYOFWEEK(`d`.`show_date`) = 7
  AND NOT EXISTS (
    SELECT 1
    FROM `showtime` `existing`
    WHERE `existing`.`hall_id` = `premium_hall`.`id`
      AND `existing`.`deleted` = 0
      AND `existing`.`start_time` < DATE_ADD(
        DATE_ADD(TIMESTAMP(`d`.`show_date`, '09:30:00'), INTERVAL `long_movie`.`duration` MINUTE),
        INTERVAL 20 MINUTE
      )
      AND DATE_ADD(`existing`.`end_time`, INTERVAL 20 MINUTE) > TIMESTAMP(`d`.`show_date`, '09:30:00')
  );

-- 常规排片：09:40、13:20、17:00、20:40，影片按日期、影厅和时段均匀轮换。
INSERT INTO `showtime`
(`movie_id`, `cinema_id`, `hall_id`, `start_time`, `end_time`, `base_price`, `total_seats`, `available_seats`, `status`)
SELECT
  `sm`.`movie_id`,
  `h`.`cinema_id`,
  `h`.`id`,
  TIMESTAMP(`d`.`show_date`, `slot`.`start_clock`),
  DATE_ADD(TIMESTAMP(`d`.`show_date`, `slot`.`start_clock`), INTERVAL `sm`.`duration` MINUTE),
  CASE `slot`.`slot_no`
    WHEN 1 THEN 32.00
    WHEN 2 THEN 39.00
    WHEN 3 THEN 45.00
    ELSE 49.00
  END
    + CASE
        WHEN `h`.`name` LIKE '%VIP%' THEN 12.00
        WHEN `h`.`name` LIKE '%IMAX%' OR `h`.`name` LIKE '%巨幕%' THEN 10.00
        WHEN `h`.`name` LIKE '%杜比%' OR `h`.`name` LIKE '%激光%' THEN 6.00
        ELSE 0.00
      END
    + CASE WHEN DAYOFWEEK(`d`.`show_date`) IN (1, 7) THEN 3.00 ELSE 0.00 END
    + MOD(`h`.`cinema_id`, 3) * 2.00,
  `h`.`total_seats`,
  `h`.`total_seats`,
  0
FROM `tmp_schedule_dates` `d`
CROSS JOIN (
  SELECT 1 AS `slot_no`, TIME('09:40:00') AS `start_clock`
  UNION ALL SELECT 2, TIME('13:20:00')
  UNION ALL SELECT 3, TIME('17:00:00')
  UNION ALL SELECT 4, TIME('20:40:00')
) `slot`
JOIN `hall` `h` ON `h`.`deleted` = 0 AND `h`.`status` = 1
JOIN `cinema` `c`
  ON `c`.`id` = `h`.`cinema_id`
 AND `c`.`deleted` = 0
 AND `c`.`status` = 1
JOIN `tmp_schedule_movies` `sm`
  ON `sm`.`show_date` = `d`.`show_date`
 AND `sm`.`movie_rank` = MOD(
   DAYOFYEAR(`d`.`show_date`) + `h`.`id` * 3 + `slot`.`slot_no` * 5,
   `sm`.`movie_count`
 ) + 1
WHERE NOT EXISTS (
  SELECT 1
  FROM `showtime` `existing`
  WHERE `existing`.`hall_id` = `h`.`id`
    AND `existing`.`deleted` = 0
    AND `existing`.`start_time` < DATE_ADD(
      DATE_ADD(TIMESTAMP(`d`.`show_date`, `slot`.`start_clock`), INTERVAL `sm`.`duration` MINUTE),
      INTERVAL 20 MINUTE
    )
    AND DATE_ADD(`existing`.`end_time`, INTERVAL 20 MINUTE) > TIMESTAMP(`d`.`show_date`, `slot`.`start_clock`)
);

DROP TEMPORARY TABLE IF EXISTS `tmp_schedule_movies`;
DROP TEMPORARY TABLE IF EXISTS `tmp_schedule_dates`;
