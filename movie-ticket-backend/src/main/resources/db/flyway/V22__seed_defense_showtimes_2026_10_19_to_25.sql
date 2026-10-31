-- 答辩演示排片：2026-10-19 至 2026-10-25（北京时间，共七天）。
-- 只补充现有营业影院、正常影厅和已上映的热映影片，不修改历史场次和订单。
-- 每厅每天四场，片长不超过 200 分钟，相邻时段至少保留 20 分钟清场。
-- 重复执行时跳过已有占厅时段，避免重复排片或与人工排片冲突。

DROP TEMPORARY TABLE IF EXISTS `tmp_defense_dates`;
CREATE TEMPORARY TABLE `tmp_defense_dates` AS
WITH RECURSIVE `dates` (`show_date`) AS (
  SELECT DATE('2026-10-19')
  UNION ALL
  SELECT DATE_ADD(`show_date`, INTERVAL 1 DAY)
  FROM `dates`
  WHERE `show_date` < DATE('2026-10-25')
)
SELECT `show_date` FROM `dates`;

DROP TEMPORARY TABLE IF EXISTS `tmp_defense_movies`;
CREATE TEMPORARY TABLE `tmp_defense_movies` AS
SELECT
  `d`.`show_date`, `m`.`id` AS `movie_id`, `m`.`duration`,
  ROW_NUMBER() OVER (
    PARTITION BY `d`.`show_date`
    ORDER BY `m`.`release_date` DESC, COALESCE(`m`.`rating`, 0) DESC, `m`.`id`
  ) AS `movie_rank`,
  COUNT(*) OVER (PARTITION BY `d`.`show_date`) AS `movie_count`
FROM `tmp_defense_dates` `d`
JOIN `movie` `m`
  ON `m`.`deleted` = 0 AND `m`.`status` = 1
 AND `m`.`release_date` <= `d`.`show_date`
 AND `m`.`duration` BETWEEN 60 AND 200;

DROP TEMPORARY TABLE IF EXISTS `tmp_defense_halls`;
CREATE TEMPORARY TABLE `tmp_defense_halls` AS
SELECT `h`.`id`, `h`.`cinema_id`, `h`.`name`, `h`.`total_seats`,
  ROW_NUMBER() OVER (ORDER BY `h`.`cinema_id`, `h`.`id`) AS `hall_rank`
FROM `hall` `h`
JOIN `cinema` `c` ON `c`.`id` = `h`.`cinema_id`
WHERE `h`.`deleted` = 0 AND `h`.`status` = 1
  AND `c`.`deleted` = 0 AND `c`.`status` = 1
  AND `h`.`total_seats` > 0
  AND `h`.`total_seats` = `h`.`total_rows` * `h`.`total_cols`;

INSERT INTO `showtime`
(`movie_id`, `cinema_id`, `hall_id`, `start_time`, `end_time`,
 `base_price`, `total_seats`, `available_seats`, `status`)
SELECT
  `m`.`movie_id`, `h`.`cinema_id`, `h`.`id`,
  TIMESTAMP(`d`.`show_date`, `slot`.`start_clock`),
  DATE_ADD(TIMESTAMP(`d`.`show_date`, `slot`.`start_clock`), INTERVAL `m`.`duration` MINUTE),
  CASE `slot`.`slot_no` WHEN 1 THEN 32.00 WHEN 2 THEN 39.00 WHEN 3 THEN 45.00 ELSE 49.00 END
    + CASE
        WHEN `h`.`name` LIKE '%VIP%' THEN 12.00
        WHEN `h`.`name` LIKE '%IMAX%' OR `h`.`name` LIKE '%巨幕%' THEN 10.00
        WHEN `h`.`name` LIKE '%杜比%' OR `h`.`name` LIKE '%激光%' THEN 6.00
        ELSE 0.00
      END
    + CASE WHEN DAYOFWEEK(`d`.`show_date`) IN (1, 7) THEN 3.00 ELSE 0.00 END
    + MOD(`h`.`cinema_id`, 3) * 2.00,
  `h`.`total_seats`, `h`.`total_seats`, 0
FROM `tmp_defense_dates` `d`
CROSS JOIN (
  SELECT 1 AS `slot_no`, TIME('09:40:00') AS `start_clock`
  UNION ALL SELECT 2, TIME('13:20:00')
  UNION ALL SELECT 3, TIME('17:00:00')
  UNION ALL SELECT 4, TIME('20:40:00')
) `slot`
CROSS JOIN `tmp_defense_halls` `h`
JOIN `tmp_defense_movies` `m`
  ON `m`.`show_date` = `d`.`show_date`
 AND `m`.`movie_rank` = MOD(
   DATEDIFF(`d`.`show_date`, DATE('2026-10-19')) * 7
     + (`h`.`hall_rank` - 1) * 4 + `slot`.`slot_no` - 1,
   `m`.`movie_count`
 ) + 1
WHERE NOT EXISTS (
  SELECT 1 FROM `showtime` `existing`
  WHERE `existing`.`hall_id` = `h`.`id` AND `existing`.`deleted` = 0
    AND `existing`.`start_time` < DATE_ADD(
      DATE_ADD(TIMESTAMP(`d`.`show_date`, `slot`.`start_clock`), INTERVAL `m`.`duration` MINUTE),
      INTERVAL 20 MINUTE)
    AND DATE_ADD(`existing`.`end_time`, INTERVAL 20 MINUTE)
        > TIMESTAMP(`d`.`show_date`, `slot`.`start_clock`)
);

DROP TEMPORARY TABLE IF EXISTS `tmp_defense_halls`;
DROP TEMPORARY TABLE IF EXISTS `tmp_defense_movies`;
DROP TEMPORARY TABLE IF EXISTS `tmp_defense_dates`;
