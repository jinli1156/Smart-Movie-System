-- 非破坏性增量脚本：保留现有业务数据，将演示目录扩充到 10 家南宁影院、40 部影片。
-- 可重复执行；影院、影厅和影片均按业务字段判重。
USE `movie_ticket`;

START TRANSACTION;

-- 将已有 5 家影院更新为南宁门店，保留原 ID 及其影厅、排片和订单关联。
UPDATE `cinema` SET
  `name` = '万达影城(青秀万达店)',
  `address` = '广西壮族自治区南宁市青秀区东葛路118号青秀万达广场',
  `phone` = '0771-1234567',
  `description` = '位于青秀区核心商圈，拥有先进的放映设备和舒适的观影环境。'
WHERE `id` = 1;

UPDATE `cinema` SET
  `name` = 'CGV影城(万象城店)',
  `address` = '广西壮族自治区南宁市青秀区民族大道136号华润万象城',
  `phone` = '0771-2345678',
  `description` = '位于东盟商务区，以高品质放映和优质服务著称。'
WHERE `id` = 2;

UPDATE `cinema` SET
  `name` = '博纳国际影城(航洋城店)',
  `address` = '广西壮族自治区南宁市青秀区民族大道131号航洋国际城',
  `phone` = '0771-3456789',
  `description` = '交通便利、商业配套丰富，为观众提供高品质观影体验。'
WHERE `id` = 3;

UPDATE `cinema` SET
  `name` = '沃美影城(江南万达店)',
  `address` = '广西壮族自治区南宁市江南区亭洪路48-1号江南万达广场',
  `phone` = '0771-4567890',
  `description` = '服务江南区观众的现代化影院，配备激光放映和沉浸式音响。'
WHERE `id` = 4;

UPDATE `cinema` SET
  `name` = 'UME国际影城(西大店)',
  `address` = '广西壮族自治区南宁市西乡塘区大学东路98号百盛步行街广场',
  `phone` = '0771-5678901',
  `description` = '邻近广西大学，为年轻观众带来多元化的观影选择。'
WHERE `id` = 5;

INSERT INTO `cinema` (`name`, `address`, `phone`, `description`, `status`)
SELECT src.`name`, src.`address`, src.`phone`, src.`description`, 1
FROM (
  SELECT '百老汇影城(会展航洋城店)' AS `name`, '广西壮族自治区南宁市青秀区民族大道131号会展航洋城' AS `address`, '0771-6713019' AS `phone`, '位于会展商圈，配备激光放映与杜比全景声影厅。' AS `description`
  UNION ALL SELECT '金逸影城(兴宁吾悦店)', '广西壮族自治区南宁市兴宁区金川路7号兴宁吾悦广场', '0771-8551806', '交通便利的城市综合体影院，提供舒适座椅和丰富餐饮服务。'
  UNION ALL SELECT '卢米埃影城(五象店)', '广西壮族自治区南宁市良庆区平乐大道37号南宁华润二十四城', '0771-5690767', '坐落于五象新区，拥有高标准激光放映设备和精品影厅。'
  UNION ALL SELECT '英皇电影城(五象万象汇店)', '广西壮族自治区南宁市良庆区金龙路2号五象万象汇', '0771-8518666', '五象新区精品都市影城，提供VIP影厅和沉浸式观影体验。'
  UNION ALL SELECT '中影国际影城(朝阳广场店)', '广西壮族自治区南宁市兴宁区朝阳路29号西南商都', '0771-6226668', '位于朝阳商圈，兼顾商业大片、经典影片与艺术电影展映。'
) src
WHERE NOT EXISTS (
  SELECT 1 FROM `cinema` existing
  WHERE existing.`name` = src.`name` AND existing.`deleted` = 0
);

-- 为新增影院各补充两个影厅。
INSERT INTO `hall` (`cinema_id`, `name`, `total_rows`, `total_cols`, `total_seats`, `status`)
SELECT c.`id`, src.`hall_name`, src.`total_rows`, src.`total_cols`, src.`total_seats`, 1
FROM (
  SELECT '百老汇影城(会展航洋城店)' AS `cinema_name`, '1号激光厅' AS `hall_name`, 10 AS `total_rows`, 16 AS `total_cols`, 160 AS `total_seats`
  UNION ALL SELECT '百老汇影城(会展航洋城店)', 'VIP厅', 6, 10, 60
  UNION ALL SELECT '金逸影城(兴宁吾悦店)', '1号厅', 11, 16, 176
  UNION ALL SELECT '金逸影城(兴宁吾悦店)', '杜比全景声厅', 12, 18, 216
  UNION ALL SELECT '卢米埃影城(五象店)', '艺术影厅', 8, 14, 112
  UNION ALL SELECT '卢米埃影城(五象店)', '激光巨幕厅', 13, 20, 260
  UNION ALL SELECT '英皇电影城(五象万象汇店)', '1号厅', 10, 16, 160
  UNION ALL SELECT '英皇电影城(五象万象汇店)', 'VIP厅', 6, 12, 72
  UNION ALL SELECT '中影国际影城(朝阳广场店)', '1号厅', 10, 15, 150
  UNION ALL SELECT '中影国际影城(朝阳广场店)', '中国巨幕厅', 14, 20, 280
) src
JOIN `cinema` c ON c.`name` = src.`cinema_name` AND c.`deleted` = 0
WHERE NOT EXISTS (
  SELECT 1 FROM `hall` existing
  WHERE existing.`cinema_id` = c.`id`
    AND existing.`name` = src.`hall_name`
    AND existing.`deleted` = 0
);

INSERT INTO `movie`
(`title`, `english_title`, `director`, `actors`, `genre`, `country`, `language`, `duration`, `release_date`, `poster`, `description`, `rating`, `status`)
SELECT src.`title`, src.`english_title`, src.`director`, src.`actors`, src.`genre`, src.`country`, src.`language`, src.`duration`, src.`release_date`, src.`poster`, src.`description`, src.`rating`, src.`status`
FROM (
  SELECT '星际穿越' AS `title`, 'Interstellar' AS `english_title`, '克里斯托弗·诺兰' AS `director`, '马修·麦康纳,安妮·海瑟薇' AS `actors`, '科幻,冒险,剧情' AS `genre`, '美国,英国' AS `country`, '英语' AS `language`, 169 AS `duration`, '2026-09-10' AS `release_date`, '/api/uploads/poster-latest-1889243.jpg' AS `poster`, '探险队穿越虫洞，为人类寻找新的家园。' AS `description`, 9.4 AS `rating`, 2 AS `status`
  UNION ALL SELECT '盗梦空间', 'Inception', '克里斯托弗·诺兰', '莱昂纳多·迪卡普里奥,约瑟夫·高登-莱维特', '科幻,悬疑,动作', '美国,英国', '英语', 148, '2026-09-12', '/api/uploads/poster-latest-3541415.jpg', '造梦师潜入他人梦境，执行一次危险的意识植入任务。', 9.3, 2
  UNION ALL SELECT '楚门的世界', 'The Truman Show', '彼得·威尔', '金·凯瑞,劳拉·琳妮', '剧情,科幻', '美国', '英语', 103, '2026-09-15', '/api/uploads/poster-latest-1292064.jpg', '一名男子逐渐发现自己的人生可能是一场大型真人秀。', 9.3, 2
  UNION ALL SELECT '千与千寻', 'Spirited Away', '宫崎骏', '柊瑠美,入野自由', '动画,奇幻,冒险', '日本', '日语', 125, '2026-09-18', '/api/uploads/poster-latest-1291561.jpg', '少女误入神灵世界，为拯救父母踏上成长之旅。', 9.4, 2
  UNION ALL SELECT '疯狂动物城2', 'Zootopia 2', '杰拉德·布什', '金妮弗·古德温,杰森·贝特曼', '动画,喜剧,冒险', '美国', '英语', 108, '2026-09-20', '/api/uploads/poster-latest-34780991.jpg', '兔朱迪与狐尼克携手调查动物城中新的神秘案件。', 8.5, 2
  UNION ALL SELECT '误杀3', 'Sheep Without a Shepherd 3', '甘剑宇', '肖央,佟丽娅,段奕宏', '悬疑,犯罪,剧情', '中国大陆', '汉语普通话', 121, '2026-09-22', '/api/uploads/poster-latest-36704893.jpg', '一场失踪案将多个家庭卷入真假难辨的局中局。', 8.0, 2
  UNION ALL SELECT '熊出没·重启未来', 'Boonie Bears: Future Reborn', '林永长', '张伟,张秉君,谭笑', '动画,科幻,喜剧', '中国大陆', '汉语普通话', 108, '2026-09-25', '/api/uploads/poster-latest-36712987.jpg', '熊大熊二与光头强进入未来世界，携手守护森林。', 7.8, 2
  UNION ALL SELECT '雄狮少年2', 'I Am What I Am 2', '孙海鹏', '李昕,郭皓,陈业雄', '动画,动作,剧情', '中国大陆', '汉语普通话', 133, '2026-09-28', '/api/uploads/poster-latest-35883131.jpg', '少年带着醒狮精神走上擂台，为梦想全力一搏。', 8.6, 2
  UNION ALL SELECT '海洋奇缘2', 'Moana 2', '小戴夫·德里克', '奥丽伊·卡瓦洛,道恩·强森', '动画,歌舞,冒险', '美国', '英语', 100, '2026-10-01', '/api/uploads/poster-latest-34603753.jpg', '莫阿娜与伙伴们驶向未知海域，寻找失落的族群。', 7.7, 2
  UNION ALL SELECT '好东西', 'Her Story', '邵艺辉', '宋佳,钟楚曦,曾慕梅', '剧情,爱情', '中国大陆', '汉语普通话', 123, '2026-10-03', '/api/uploads/poster-latest-36154853.jpg', '几位都市女性在生活碰撞中建立友谊并重新认识自己。', 9.0, 2
) src
WHERE NOT EXISTS (
  SELECT 1 FROM `movie` existing
  WHERE existing.`title` = src.`title`
    AND existing.`release_date` = src.`release_date`
    AND existing.`deleted` = 0
);

COMMIT;

SELECT COUNT(*) AS `影院数量` FROM `cinema` WHERE `deleted` = 0;
SELECT COUNT(*) AS `影厅数量` FROM `hall` WHERE `deleted` = 0;
SELECT COUNT(*) AS `影片数量` FROM `movie` WHERE `deleted` = 0;
