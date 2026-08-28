USE movie_ticket;

-- =============================================
-- 测试数据初始化脚本（幂等，可重复执行）
-- 注意：会清空已有测试数据后重新插入
-- =============================================

SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE `comment`;
TRUNCATE TABLE `ticket_order`;
TRUNCATE TABLE `showtime`;
TRUNCATE TABLE `movie`;
TRUNCATE TABLE `hall`;
TRUNCATE TABLE `cinema`;
TRUNCATE TABLE `user`;

SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO `user` (`username`, `password`, `nickname`, `email`, `phone`, `role`, `status`) VALUES
('user1', '$2b$10$QsfesUrhW52QfOZmEhgDU.tX31I9JIMAWY6xjBzkwEvL5gFGVPROe', '测试用户', 'user@example.com', '13800138000', 'USER', 1),
('admin', '$2b$10$QsfesUrhW52QfOZmEhgDU.tX31I9JIMAWY6xjBzkwEvL5gFGVPROe', '系统管理员', 'admin@example.com', '13800138001', 'ADMIN', 1),
('cinema_admin', '$2b$10$QsfesUrhW52QfOZmEhgDU.tX31I9JIMAWY6xjBzkwEvL5gFGVPROe', '影院管理员', 'cinema@example.com', '13800138002', 'CINEMA_ADMIN', 1);
-- 三个测试账号的原始密码均为：admin123

-- 插入影院数据
INSERT INTO `cinema` (`name`, `address`, `phone`, `description`, `status`) VALUES
('万达影城(朝阳店)', '北京市朝阳区建国路93号万达广场', '010-12345678', '万达影城是全球领先的电影院线品牌，拥有先进的放映设备和舒适的观影环境。', 1),
('CGV影城(三里屯店)', '北京市朝阳区三里屯路19号三里屯太古里', '010-23456789', 'CGV影城源自韩国，以高品质的观影体验和优质服务著称。', 1),
('博纳国际影城(悦荟店)', '北京市朝阳区朝阳北路101号朝阳大悦城', '010-34567890', '博纳国际影城致力于为观众提供最佳的观影享受。', 1),
('耀莱成龙国际影城(五棵松店)', '北京市海淀区复兴路69号五棵松体育馆', '010-45678901', '耀莱成龙国际影城，成龙大哥倾力打造的高端影城品牌。', 1),
('UME国际影城(双井店)', '北京市朝阳区广渠路28号华联购物中心', '010-56789012', 'UME国际影城，为您带来国际化的观影体验。', 1);

-- 插入影厅数据
INSERT INTO `hall` (`cinema_id`, `name`, `total_rows`, `total_cols`, `total_seats`, `status`) VALUES
(1, '1号厅', 10, 15, 150, 1),
(1, '2号厅', 12, 18, 216, 1),
(1, 'VIP厅', 5, 10, 50, 1),
(2, '1号厅', 10, 15, 150, 1),
(2, '2号厅', 12, 18, 216, 1),
(3, '1号厅', 10, 15, 150, 1),
(3, 'IMAX厅', 15, 20, 300, 1),
(4, '1号厅', 10, 15, 150, 1),
(4, '杜比全景声厅', 12, 18, 216, 1),
(5, '1号厅', 10, 15, 150, 1);

-- 插入电影数据
INSERT INTO `movie` (`title`, `english_title`, `director`, `actors`, `genre`, `country`, `language`, `duration`, `release_date`, `description`, `rating`, `status`) VALUES
('流浪地球3', 'The Wandering Earth 3', '郭帆', '吴京,李雪健,沙溢', '科幻,冒险', '中国', '汉语普通话', 180, '2026-02-01', '太阳即将毁灭，人类在地球表面建造出巨大的推进器，寻找新的家园。然而宇宙之路危机四伏，为了拯救地球，为了人类能在漫长的2500年后抵达新的家园，流浪地球时代的年轻人再次挺身而出，展开争分夺秒的生死之战。', 8.5, 1),
('长安三万里', 'Chang An', '谢君伟,邹靖', '杨天翔,凌振赫', '动画,历史', '中国', '汉语普通话', 168, '2026-01-15', '故事发生在盛唐时期，讲述了诗人高适与李白的传奇友谊，以及他们在大唐由盛转衰的历史洪流中的人生际遇。', 8.3, 1),
('封神第二部：战火西岐', 'Creation of the Gods II', '乌尔善', '黄渤,费翔,李雪健', '奇幻,动作', '中国', '汉语普通话', 150, '2026-03-01', '商朝末年，纣王无道，姬发率领西岐大军讨伐商纣，一场惊天动地的神魔大战即将展开。', 8.0, 2),
('沙丘3', 'Dune: Part Three', '丹尼斯·维伦纽瓦', '提莫西·查拉梅,赞达亚', '科幻,冒险', '美国', '英语', 165, '2026-04-15', '保罗·厄崔迪与契妮以及弗雷曼人联手，向毁灭他家族的阴谋者展开复仇。', 8.7, 2),
('功夫熊猫5', 'Kung Fu Panda 5', '迈克·米切尔', '杰克·布莱克,达斯汀·霍夫曼', '动画,喜剧', '美国', '英语', 95, '2026-03-20', '阿宝继续他的功夫之旅，这次他将面对前所未有的挑战，并发现真正的内心平静。', 7.8, 2),
('你好，李焕英', 'Hi, Mom', '贾玲', '贾玲,张小斐,沈腾', '喜剧,奇幻', '中国', '汉语普通话', 128, '2025-12-25', '刚考上大学的女孩贾晓玲经历了一次意外，穿越回到了1981年，遇见了年轻时的母亲李焕英。', 7.9, 1),
('满江红', 'Full River Red', '张艺谋', '沈腾,易烊千玺,张译', '悬疑,喜剧', '中国', '汉语普通话', 159, '2025-11-30', '南宋绍兴年间，岳飞死后四年，秦桧率兵与金国会谈。会谈前夜，金国使者死在宰相驻地，所携密信也不翼而飞。', 7.6, 1),
('阿凡达3：带种者', 'Avatar 3: The Seed Bearer', '詹姆斯·卡梅隆', '萨姆·沃辛顿,佐伊·索尔达娜', '科幻,冒险', '美国', '英语', 190, '2026-05-20', '杰克和奈蒂莉的家庭继续在潘多拉星球上成长，他们将探索新的部落和面对新的威胁。', 8.8, 2),
('哪吒之魔童闹海', 'Ne Zha 2', '饺子', '囧森瑟夫,瀚墨', '动画,奇幻', '中国', '汉语普通话', 110, '2026-02-15', '哪吒的新冒险，这次他将面对更强大的敌人，并揭开自己身世的更多秘密。', 8.2, 2),
('速度与激情11', 'Fast & Furious 11', '路易斯·莱特里尔', '范·迪塞尔,米歇尔·罗德里格兹', '动作,犯罪', '美国', '英语', 140, '2026-06-01', '唐老大和他的家人将面对最后的挑战，这是速度与激情系列的终章。', 7.5, 2);

-- 插入场次数据（今天和未来几天的场次）
-- 今天的场次
INSERT INTO `showtime` (`movie_id`, `cinema_id`, `hall_id`, `start_time`, `end_time`, `base_price`, `total_seats`, `available_seats`, `status`) VALUES
(1, 1, 1, DATE_ADD(NOW(), INTERVAL 2 HOUR), DATE_ADD(NOW(), INTERVAL 5 HOUR), 45.00, 150, 150, 0),
(1, 1, 2, DATE_ADD(NOW(), INTERVAL 4 HOUR), DATE_ADD(NOW(), INTERVAL 7 HOUR), 45.00, 216, 216, 0),
(2, 1, 1, DATE_ADD(NOW(), INTERVAL 6 HOUR), DATE_ADD(NOW(), INTERVAL 9 HOUR), 40.00, 150, 150, 0),
(6, 2, 1, DATE_ADD(NOW(), INTERVAL 3 HOUR), DATE_ADD(NOW(), INTERVAL 5 HOUR), 42.00, 150, 150, 0),
(7, 2, 2, DATE_ADD(NOW(), INTERVAL 5 HOUR), DATE_ADD(NOW(), INTERVAL 8 HOUR), 48.00, 216, 216, 0),
(1, 3, 7, DATE_ADD(NOW(), INTERVAL 2 HOUR), DATE_ADD(NOW(), INTERVAL 5 HOUR), 80.00, 300, 300, 0);

-- 明天的场次
INSERT INTO `showtime` (`movie_id`, `cinema_id`, `hall_id`, `start_time`, `end_time`, `base_price`, `total_seats`, `available_seats`, `status`) VALUES
(1, 1, 1, DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 1 DAY), INTERVAL 10 HOUR), DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 1 DAY), INTERVAL 13 HOUR), 45.00, 150, 150, 0),
(1, 1, 2, DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 1 DAY), INTERVAL 14 HOUR), DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 1 DAY), INTERVAL 17 HOUR), 45.00, 216, 216, 0),
(2, 1, 1, DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 1 DAY), INTERVAL 16 HOUR), DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 1 DAY), INTERVAL 19 HOUR), 40.00, 150, 150, 0),
(6, 2, 1, DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 1 DAY), INTERVAL 11 HOUR), DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 1 DAY), INTERVAL 13 HOUR), 42.00, 150, 150, 0),
(7, 2, 2, DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 1 DAY), INTERVAL 15 HOUR), DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 1 DAY), INTERVAL 18 HOUR), 48.00, 216, 216, 0),
(1, 3, 7, DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 1 DAY), INTERVAL 13 HOUR), DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 1 DAY), INTERVAL 16 HOUR), 80.00, 300, 300, 0),
(2, 3, 1, DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 1 DAY), INTERVAL 17 HOUR), DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 1 DAY), INTERVAL 20 HOUR), 40.00, 150, 150, 0);

-- 后天的场次
INSERT INTO `showtime` (`movie_id`, `cinema_id`, `hall_id`, `start_time`, `end_time`, `base_price`, `total_seats`, `available_seats`, `status`) VALUES
(1, 1, 1, DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 2 DAY), INTERVAL 10 HOUR), DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 2 DAY), INTERVAL 13 HOUR), 45.00, 150, 150, 0),
(1, 1, 2, DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 2 DAY), INTERVAL 14 HOUR), DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 2 DAY), INTERVAL 17 HOUR), 45.00, 216, 216, 0),
(2, 1, 1, DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 2 DAY), INTERVAL 16 HOUR), DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 2 DAY), INTERVAL 19 HOUR), 40.00, 150, 150, 0),
(6, 2, 1, DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 2 DAY), INTERVAL 11 HOUR), DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 2 DAY), INTERVAL 13 HOUR), 42.00, 150, 150, 0),
(7, 2, 2, DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 2 DAY), INTERVAL 15 HOUR), DATE_ADD(DATE_ADD(CURDATE(), INTERVAL 2 DAY), INTERVAL 18 HOUR), 48.00, 216, 216, 0);

-- 完成提示
SELECT '测试数据导入完成！' AS message;
SELECT CONCAT('影院数量: ', COUNT(*)) AS info FROM cinema;
SELECT CONCAT('影厅数量: ', COUNT(*)) AS info FROM hall;
SELECT CONCAT('电影数量: ', COUNT(*)) AS info FROM movie;
SELECT CONCAT('场次数量: ', COUNT(*)) AS info FROM showtime;
