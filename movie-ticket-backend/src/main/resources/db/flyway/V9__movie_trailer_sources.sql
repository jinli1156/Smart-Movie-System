-- 为演示影片补齐已核验的公开预告入口；管理员已经配置的地址保持不变。
UPDATE `movie`
SET `trailer` = 'https://www.bilibili.com/video/BV1XM411D7b4/'
WHERE `title` = '流浪地球3' AND (`trailer` IS NULL OR `trailer` = '');

UPDATE `movie`
SET `trailer` = 'https://www.youtube.com/watch?v=xYQ65FNLJvU'
WHERE `title` = '封神第二部：战火西岐' AND (`trailer` IS NULL OR `trailer` = '');

UPDATE `movie`
SET `trailer` = 'https://www.youtube.com/watch?v=EBLY_sez_LI'
WHERE `title` = '哪吒之魔童闹海' AND (`trailer` IS NULL OR `trailer` = '');

UPDATE `movie`
SET `trailer` = 'https://www.youtube.com/watch?v=1nrBhJE2w7E'
WHERE `title` = '满江红' AND (`trailer` IS NULL OR `trailer` = '');
