-- 未上映或已下架影片不得提供可购场次；使用软删除保留历史关联。
UPDATE `showtime` `s`
JOIN `movie` `m` ON `m`.`id` = `s`.`movie_id`
SET
  `s`.`status` = 2,
  `s`.`available_seats` = 0,
  `s`.`deleted` = 1
WHERE `s`.`deleted` = 0
  AND `s`.`status` = 0
  AND `s`.`start_time` >= '2026-09-05 00:00:00'
  AND `s`.`start_time` < '2026-10-01 00:00:00'
  AND `m`.`status` <> 1;
