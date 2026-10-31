USE movie_ticket;
SET NAMES utf8mb4;

-- 开发/演示重置：清空全部业务数据。请先停止后端，禁止在需保留数据的库执行。
-- 先启动新版后端完成 Flyway V24，再运行本文件；失败时事务回滚。
-- 日期采用北京时间：执行当天至未来第 10 天（含首尾共 11 天），当天跳过开场前不足 15 分钟的时段。
-- 演示对象使用显式 ID，重复执行不依赖自增计数，也不清空 Flyway 历史和迁移归档。
DROP PROCEDURE IF EXISTS `reset_movie_ticket_demo`;
DELIMITER $$
CREATE PROCEDURE `reset_movie_ticket_demo`()
BEGIN
  DECLARE v_seed_now DATETIME;
  DECLARE v_seed_today DATE;
  DECLARE v_lock_acquired INT DEFAULT 0;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    IF v_lock_acquired = 1 THEN
      DO RELEASE_LOCK(CONCAT('movie_demo:', MD5(DATABASE())));
    END IF;
    RESIGNAL;
  END;

  -- 在任何 DELETE 前确认全部依赖表和迁移版本，旧库不允许带病重置。
  IF (SELECT COUNT(*) FROM information_schema.TABLES
       WHERE TABLE_SCHEMA = DATABASE() AND ENGINE = 'InnoDB' AND TABLE_NAME IN
         ('user','cinema','hall','movie','showtime','ticket_order','user_coupon','comment',
          'user_movie_behavior','coupon_code','coupon_code_redemption','assistant_conversation',
          'assistant_turn','assistant_preference','assistant_checkout','scheduling_saved_draft',
          'flyway_schema_history')) <> 17 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Demo reset aborted: start the backend and complete Flyway migrations first';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '24' AND success = 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Demo reset aborted: Flyway V24 has not completed';
  END IF;
  IF @@session.foreign_key_checks <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Demo reset requires FOREIGN_KEY_CHECKS=1';
  END IF;
  SELECT GET_LOCK(CONCAT('movie_demo:', MD5(DATABASE())), 0) INTO v_lock_acquired;
  IF COALESCE(v_lock_acquired, 0) <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Another demo reset is running';
  END IF;
  SET v_seed_now = DATE_ADD(UTC_TIMESTAMP(), INTERVAL 8 HOUR);
  SET v_seed_today = DATE(v_seed_now);

  START TRANSACTION;
  -- 解开优惠券与订单的双向引用，全程保留外键检查。
  UPDATE ticket_order SET coupon_id = NULL;
  UPDATE user_coupon SET order_id = NULL;
  DELETE FROM assistant_turn;
  DELETE FROM assistant_conversation;
  DELETE FROM assistant_preference;
  DELETE FROM assistant_checkout;
  DELETE FROM scheduling_saved_draft;
  DELETE FROM coupon_code_redemption;
  DELETE FROM `comment`;
  DELETE FROM user_movie_behavior;
  DELETE FROM user_coupon;
  DELETE FROM coupon_code;
  DELETE FROM ticket_order;
  DELETE FROM showtime;
  DELETE FROM movie;
  DELETE FROM hall;
  -- 影院管理员引用影院，先清用户再清影院。
  DELETE FROM `user`;
  DELETE FROM cinema;

INSERT INTO `user` (`id`, `username`, `password`, `nickname`, `email`, `phone`, `role`, `status`) VALUES
(1, 'user1', '$2b$10$QsfesUrhW52QfOZmEhgDU.tX31I9JIMAWY6xjBzkwEvL5gFGVPROe', '测试用户', 'user@example.com', '13800138000', 'USER', 1),
(2, 'admin', '$2b$10$QsfesUrhW52QfOZmEhgDU.tX31I9JIMAWY6xjBzkwEvL5gFGVPROe', '系统管理员', 'admin@example.com', '13800138001', 'ADMIN', 1),
(3, 'cinema_admin', '$2b$10$QsfesUrhW52QfOZmEhgDU.tX31I9JIMAWY6xjBzkwEvL5gFGVPROe', '影院管理员', 'cinema@example.com', '13800138002', 'CINEMA_ADMIN', 1);
-- 三个测试账号的原始密码均为：admin123

-- 插入影院数据
INSERT INTO `cinema` (`id`, `name`, `address`, `phone`, `description`, `images`, `status`) VALUES
(1, '万达影城(青秀万达店)', '广西壮族自治区南宁市青秀区东葛路118号青秀万达广场', '0771-1234567', '位于青秀区核心商圈，拥有先进的放映设备和舒适的观影环境。', 'https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=900&q=82', 1),
(2, 'CGV影城(万象城店)', '广西壮族自治区南宁市青秀区民族大道136号华润万象城', '0771-2345678', '位于东盟商务区，以高品质放映和优质服务著称。', 'https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?auto=format&fit=crop&w=900&q=82', 1),
(3, '博纳国际影城(航洋城店)', '广西壮族自治区南宁市青秀区民族大道131号航洋国际城', '0771-3456789', '交通便利、商业配套丰富，为观众提供高品质观影体验。', 'https://images.unsplash.com/photo-1485846234645-a62644f84728?auto=format&fit=crop&w=900&q=82', 1),
(4, '沃美影城(江南万达店)', '广西壮族自治区南宁市江南区亭洪路48-1号江南万达广场', '0771-4567890', '服务江南区观众的现代化影院，配备激光放映和沉浸式音响。', 'https://images.unsplash.com/photo-1574267432553-4b4628081c31?auto=format&fit=crop&w=900&q=82', 1),
(5, 'UME国际影城(西大店)', '广西壮族自治区南宁市西乡塘区大学东路98号百盛步行街广场', '0771-5678901', '邻近广西大学，为年轻观众带来多元化的观影选择。', 'https://images.unsplash.com/photo-1598899134739-24c46f58b8c0?auto=format&fit=crop&w=900&q=82', 1),
(6, '百老汇影城(会展航洋城店)', '广西壮族自治区南宁市青秀区民族大道131号会展航洋城', '0771-6713019', '位于会展商圈，配备激光放映与杜比全景声影厅。', 'https://images.unsplash.com/photo-1586899028174-e7098604235b?auto=format&fit=crop&w=900&q=82', 1),
(7, '金逸影城(兴宁吾悦店)', '广西壮族自治区南宁市兴宁区金川路7号兴宁吾悦广场', '0771-8551806', '交通便利的城市综合体影院，提供舒适座椅和丰富餐饮服务。', 'https://images.unsplash.com/photo-1543536448-d209d2d13a1c?auto=format&fit=crop&w=900&q=82', 1),
(8, '卢米埃影城(五象店)', '广西壮族自治区南宁市良庆区平乐大道37号南宁华润二十四城', '0771-5690767', '坐落于五象新区，拥有高标准激光放映设备和精品影厅。', 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=900&q=82', 1),
(9, '英皇电影城(五象万象汇店)', '广西壮族自治区南宁市良庆区金龙路2号五象万象汇', '0771-8518666', '五象新区精品都市影城，提供VIP影厅和沉浸式观影体验。', 'https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=900&q=82', 1),
(10, '中影国际影城(朝阳广场店)', '广西壮族自治区南宁市兴宁区朝阳路29号西南商都', '0771-6226668', '位于朝阳商圈，兼顾商业大片、经典影片与艺术电影展映。', 'https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?auto=format&fit=crop&w=900&q=82', 1);

-- 插入影厅数据
INSERT INTO `hall` (`id`, `cinema_id`, `name`, `total_rows`, `total_cols`, `total_seats`, `status`) VALUES
(1, 1, '1号厅', 10, 15, 150, 1),
(2, 1, '2号厅', 12, 18, 216, 1),
(3, 1, 'VIP厅', 5, 10, 50, 1),
(4, 2, '1号厅', 10, 15, 150, 1),
(5, 2, '2号厅', 12, 18, 216, 1),
(6, 3, '1号厅', 10, 15, 150, 1),
(7, 3, 'IMAX厅', 15, 20, 300, 1),
(8, 4, '1号厅', 10, 15, 150, 1),
(9, 4, '杜比全景声厅', 12, 18, 216, 1),
(10, 5, '1号厅', 10, 15, 150, 1),
(11, 6, '1号激光厅', 10, 16, 160, 1),
(12, 6, 'VIP厅', 6, 10, 60, 1),
(13, 7, '1号厅', 11, 16, 176, 1),
(14, 7, '杜比全景声厅', 12, 18, 216, 1),
(15, 8, '艺术影厅', 8, 14, 112, 1),
(16, 8, '激光巨幕厅', 13, 20, 260, 1),
(17, 9, '1号厅', 10, 16, 160, 1),
(18, 9, 'VIP厅', 6, 12, 72, 1),
(19, 10, '1号厅', 10, 15, 150, 1),
(20, 10, '中国巨幕厅', 14, 20, 280, 1);

-- 插入电影数据
INSERT INTO `movie` (`id`, `title`, `english_title`, `director`, `actors`, `genre`, `country`, `language`, `duration`, `release_date`, `poster`, `description`, `rating`, `status`) VALUES
(1, '流浪地球3', 'The Wandering Earth 3', '郭帆', '吴京,李雪健,沙溢', '科幻,冒险', '中国', '汉语普通话', 180, '2026-02-01', '/api/uploads/poster-movie-01.jpg', '太阳即将毁灭，人类在地球表面建造出巨大的推进器，寻找新的家园。然而宇宙之路危机四伏，为了拯救地球，为了人类能在漫长的2500年后抵达新的家园，流浪地球时代的年轻人再次挺身而出，展开争分夺秒的生死之战。', 8.5, 1),
(2, '长安三万里', 'Chang An', '谢君伟,邹靖', '杨天翔,凌振赫', '动画,历史', '中国', '汉语普通话', 168, '2026-01-15', '/api/uploads/poster-movie-02.webp', '故事发生在盛唐时期，讲述了诗人高适与李白的传奇友谊，以及他们在大唐由盛转衰的历史洪流中的人生际遇。', 8.3, 1),
(3, '封神第二部：战火西岐', 'Creation of the Gods II', '乌尔善', '黄渤,费翔,李雪健', '奇幻,动作', '中国', '汉语普通话', 150, '2026-03-01', '/api/uploads/poster-movie-03.jpg', '商朝末年，纣王无道，姬发率领西岐大军讨伐商纣，一场惊天动地的神魔大战即将展开。', 8.0, 2),
(4, '沙丘3', 'Dune: Part Three', '丹尼斯·维伦纽瓦', '提莫西·查拉梅,赞达亚', '科幻,冒险', '美国', '英语', 165, '2026-04-15', '/api/uploads/poster-movie-04.jpg', '保罗·厄崔迪与契妮以及弗雷曼人联手，向毁灭他家族的阴谋者展开复仇。', 8.7, 2),
(5, '功夫熊猫5', 'Kung Fu Panda 5', '迈克·米切尔', '杰克·布莱克,达斯汀·霍夫曼', '动画,喜剧', '美国', '英语', 95, '2026-03-20', '/api/uploads/poster-movie-05.jpg', '阿宝继续他的功夫之旅，这次他将面对前所未有的挑战，并发现真正的内心平静。', 7.8, 2),
(6, '你好，李焕英', 'Hi, Mom', '贾玲', '贾玲,张小斐,沈腾', '喜剧,奇幻', '中国', '汉语普通话', 128, '2025-12-25', '/api/uploads/poster-movie-06.jpg', '刚考上大学的女孩贾晓玲经历了一次意外，穿越回到了1981年，遇见了年轻时的母亲李焕英。', 7.9, 1),
(7, '满江红', 'Full River Red', '张艺谋', '沈腾,易烊千玺,张译', '悬疑,喜剧', '中国', '汉语普通话', 159, '2025-11-30', '/api/uploads/poster-movie-07.jpg', '南宋绍兴年间，岳飞死后四年，秦桧率兵与金国会谈。会谈前夜，金国使者死在宰相驻地，所携密信也不翼而飞。', 7.6, 1),
(8, '阿凡达3：带种者', 'Avatar 3: The Seed Bearer', '詹姆斯·卡梅隆', '萨姆·沃辛顿,佐伊·索尔达娜', '科幻,冒险', '美国', '英语', 190, '2026-05-20', '/api/uploads/poster-movie-08.jpeg', '杰克和奈蒂莉的家庭继续在潘多拉星球上成长，他们将探索新的部落和面对新的威胁。', 8.8, 2),
(9, '哪吒之魔童闹海', 'Ne Zha 2', '饺子', '囧森瑟夫,瀚墨', '动画,奇幻', '中国', '汉语普通话', 110, '2026-02-15', '/api/uploads/poster-movie-09.jpg', '哪吒的新冒险，这次他将面对更强大的敌人，并揭开自己身世的更多秘密。', 8.2, 2),
(10, '速度与激情11', 'Fast & Furious 11', '路易斯·莱特里尔', '范·迪塞尔,米歇尔·罗德里格兹', '动作,犯罪', '美国', '英语', 140, '2026-06-01', '/api/uploads/poster-movie-10.jpg', '唐老大和他的家人将面对最后的挑战，这是速度与激情系列的终章。', 7.5, 2),
(11, '奥德赛', 'The Odyssey', '克里斯托弗·诺兰', '马特·达蒙,汤姆·霍兰德,安妮·海瑟薇', '动作,历史,奇幻,冒险', '美国,英国', '英语', 173, '2026-08-14', '/api/uploads/poster-latest-36808876.jpg', '改编自荷马史诗，讲述奥德修斯历经艰险返回故乡的传奇旅程。', 8.5, 1),
(12, '欢迎来龙餐馆', '', '文牧野', '沈腾,蒋奇明,李治廷', '剧情,战争', '中国大陆', '汉语普通话,阿拉伯语', 140, '2026-08-11', '/api/uploads/poster-latest-35811064.jpg', '两名中国人在中东经营餐馆，战争爆发后直面命运与人性的考验。', 8.7, 1),
(13, '空枪', '', '韩延', '朱一龙,檀健次,梁家辉', '剧情,犯罪', '中国大陆,中国香港', '粤语,汉语普通话', 160, '2026-08-19', '/api/uploads/poster-latest-37508847.jpg', '九十年代的欲望之城中，一场巨款劫案引发正邪角力。', 7.2, 1),
(14, '肖申克的救赎', 'The Shawshank Redemption', '弗兰克·德拉邦特', '蒂姆·罗宾斯,摩根·弗里曼', '剧情,犯罪', '美国', '英语', 142, '2026-08-28', '/api/uploads/poster-latest-1292052.jpg', '蒙冤入狱的银行家安迪在监狱中坚守希望并完成自我救赎。', 9.7, 1),
(15, '数到三', '', '孙琳', '惠英红,张宥浩,潘斌龙', '剧情', '中国大陆', '汉语普通话', 107, '2026-08-28', '/api/uploads/poster-latest-36931140.jpg', '失去儿子的母亲与接受心脏移植的青年相遇，温情背后隐藏秘密。', 7.3, 1),
(16, '牛来', '', '信雨萌', '信雨萌,孙丽芳', '动画', '中国大陆', '汉语普通话', 86, '2026-08-05', '/api/uploads/poster-latest-38581618.jpg', '一只小牛犊在旅途中成长为勇敢担当的勇士。', 6.0, 1),
(17, '密档', '', '郑大圣', '袁弘,李妍锡,邵峰', '剧情,悬疑', '中国大陆', '汉语普通话', 132, '2026-08-28', '/api/uploads/poster-latest-35209727.jpg', '一九四二年的上海，地下党员夫妇在危机中守护重要机要卷宗。', 7.5, 1),
(18, '痴迷', 'Obsession', '库里·巴克', '迈克尔·约翰斯顿,印达·纳瓦雷特', '恐怖,奇幻', '美国', '英语', 110, '2026-07-24', '/api/uploads/poster-latest-37450627.jpg', '青年借助禁忌传说实现爱情愿望，却召来蚕食现实的古老存在。', 7.6, 1),
(19, '蜘蛛侠：崭新之日', 'Spider-Man: Brand New Day', '德斯汀·丹尼尔·克雷顿', '汤姆·霍兰德,赞达亚,萨迪·辛克', '动作,科幻,冒险', '美国,英国', '英语', 145, '2026-07-29', '/api/uploads/poster-latest-36246195.jpg', '被世界遗忘的彼得·帕克独自守护纽约并面对全新危机。', 7.8, 1),
(20, '大唐妖探', '', '程腾,黄珉', '雷淞然,张呈,蔡海婷', '喜剧,动画,悬疑', '中国大陆', '汉语普通话', 117, '2026-08-22', '/api/uploads/poster-latest-35464353.jpg', '天才少年与狼妖捕快组队，在奇幻长安追查离奇命案。', 7.4, 1),
(21, '八仙！', '', '牟正洋', '陈浩,李绍哲,立冬', '喜剧,动画,奇幻', '中国大陆', '汉语普通话', 144, '2026-07-18', '/api/uploads/poster-latest-36882191.jpg', '八位身份各异的凡人组成团队，共同面对一场惊天阴谋。', 8.2, 1),
(22, '抓特务', '', '冯小刚', '雷佳音,胡歌,啜妮', '剧情', '中国大陆', '汉语普通话', 141, '2026-06-19', '/api/uploads/poster-latest-36812879.jpg', '新中国成立初期，派出所所长在胡同中追查潜伏特务。', 7.4, 1),
(23, '机器人总动员', 'WALL·E', '安德鲁·斯坦顿', '本·贝尔特,艾丽莎·奈特', '科幻,动画,冒险', '美国', '英语', 98, '2026-08-19', '/api/uploads/poster-latest-2131459.jpg', '清理地球垃圾的机器人瓦力遇见伊芙，开启跨越宇宙的冒险。', 9.3, 1),
(24, '年会不能停！2', '', '董润年', '张若昀,白客,高叶', '喜剧', '中国大陆', '汉语普通话', 125, '2026-08-01', '/api/uploads/poster-latest-36850814.jpg', '职场搭档陷入失败便会时间倒流的循环，并追查公司真相。', 6.6, 1),
(25, '去你的岛', '', '周浩然', '杨茜云,张福正,孙婉瑜', '爱情,动画,奇幻', '中国大陆', '汉语普通话', 119, '2026-08-07', '/api/uploads/poster-latest-35275131.jpg', '女孩进入由记忆构成的奇幻岛屿，重新理解爱与分离。', 7.6, 1),
(26, '杀死比尔：血色全传', 'Kill Bill: The Whole Bloody Affair', '昆汀·塔伦蒂诺', '乌玛·瑟曼,刘玉玲', '动作,犯罪', '美国', '英语', 276, '2026-09-04', '/api/uploads/poster-latest-10756537.jpg', '遭暗杀后苏醒的新娘踏上寻找幕后首领的复仇之路。', 8.7, 2),
(27, '坠落2：死点', 'Fall 2: Deadpoint', '迈克尔·斯派瑞,彼得·斯派瑞', '哈丽特·斯莱特,维吉尼亚·加德纳', '惊悚,冒险', '美国,英国', '英语', 98, '2026-09-04', '/api/uploads/poster-latest-36318704.jpg', '极限爱好者在高空栈道遭遇灾害，只能依靠有限物资求生。', 7.1, 2),
(28, '怒之杀', 'Mutiny', '让-弗朗索瓦·雷切', '杰森·斯坦森,安娜贝拉·沃丽丝', '动作,惊悚,犯罪', '英国,美国', '英语', 96, '2026-09-04', '/api/uploads/poster-latest-36889088.jpg', '贴身保镖被诬陷杀害雇主，闯入远洋货轮揭开阴谋。', 7.0, 2),
(29, '怪屋', '', '王博伦', '傅菁,思漩,苏劲源', '悬疑,惊悚,恐怖', '中国大陆', '汉语普通话', 107, '2026-09-04', '/api/uploads/poster-latest-37427091.jpg', '试睡员与博主入住法拍别墅，诡异事件接连出现。', 6.8, 2),
(30, '燃烧吧！爸爸', '', '刘潇阳', '文淇,倪虹洁,喻恩泰', '剧情,喜剧', '中国大陆', '汉语普通话,沪语', 102, '2026-09-05', '/api/uploads/poster-latest-36903929.jpg', '一对母女在处理父亲身后事时重新理解彼此。', 7.2, 2),
(31, '星际穿越', 'Interstellar', '克里斯托弗·诺兰', '马修·麦康纳,安妮·海瑟薇', '科幻,冒险,剧情', '美国,英国', '英语', 169, '2026-09-10', '/api/uploads/poster-latest-1889243.jpg', '探险队穿越虫洞，为人类寻找新的家园。', 9.4, 2),
(32, '盗梦空间', 'Inception', '克里斯托弗·诺兰', '莱昂纳多·迪卡普里奥,约瑟夫·高登-莱维特', '科幻,悬疑,动作', '美国,英国', '英语', 148, '2026-09-12', '/api/uploads/poster-latest-3541415.jpg', '造梦师潜入他人梦境，执行一次危险的意识植入任务。', 9.3, 2),
(33, '楚门的世界', 'The Truman Show', '彼得·威尔', '金·凯瑞,劳拉·琳妮', '剧情,科幻', '美国', '英语', 103, '2026-09-15', '/api/uploads/poster-latest-1292064.jpg', '一名男子逐渐发现自己的人生可能是一场大型真人秀。', 9.3, 2),
(34, '千与千寻', 'Spirited Away', '宫崎骏', '柊瑠美,入野自由', '动画,奇幻,冒险', '日本', '日语', 125, '2026-09-18', '/api/uploads/poster-latest-1291561.jpg', '少女误入神灵世界，为拯救父母踏上成长之旅。', 9.4, 2),
(35, '疯狂动物城2', 'Zootopia 2', '杰拉德·布什', '金妮弗·古德温,杰森·贝特曼', '动画,喜剧,冒险', '美国', '英语', 108, '2026-09-20', '/api/uploads/poster-latest-34780991.jpg', '兔朱迪与狐尼克携手调查动物城中新的神秘案件。', 8.5, 2),
(36, '误杀3', 'Sheep Without a Shepherd 3', '甘剑宇', '肖央,佟丽娅,段奕宏', '悬疑,犯罪,剧情', '中国大陆', '汉语普通话', 121, '2026-09-22', '/api/uploads/poster-latest-36704893.jpg', '一场失踪案将多个家庭卷入真假难辨的局中局。', 8.0, 2),
(37, '熊出没·重启未来', 'Boonie Bears: Future Reborn', '林永长', '张伟,张秉君,谭笑', '动画,科幻,喜剧', '中国大陆', '汉语普通话', 108, '2026-09-25', '/api/uploads/poster-latest-36712987.jpg', '熊大熊二与光头强进入未来世界，携手守护森林。', 7.8, 2),
(38, '雄狮少年2', 'I Am What I Am 2', '孙海鹏', '李昕,郭皓,陈业雄', '动画,动作,剧情', '中国大陆', '汉语普通话', 133, '2026-09-28', '/api/uploads/poster-latest-35883131.jpg', '少年带着醒狮精神走上擂台，为梦想全力一搏。', 8.6, 2),
(39, '海洋奇缘2', 'Moana 2', '小戴夫·德里克', '奥丽伊·卡瓦洛,道恩·强森', '动画,歌舞,冒险', '美国', '英语', 100, '2026-10-01', '/api/uploads/poster-latest-34603753.jpg', '莫阿娜与伙伴们驶向未知海域，寻找失落的族群。', 7.7, 2),
(40, '好东西', 'Her Story', '邵艺辉', '宋佳,钟楚曦,曾慕梅', '剧情,爱情', '中国大陆', '汉语普通话', 123, '2026-10-03', '/api/uploads/poster-latest-36154853.jpg', '几位都市女性在生活碰撞中建立友谊并重新认识自己。', 9.0, 2);

-- 详情页预告片素材（公开发布页面；页面会根据平台自动转换为内嵌播放器）
UPDATE `movie` SET `trailer` = 'https://www.bilibili.com/video/BV1XM411D7b4/' WHERE `title` = '流浪地球3';
UPDATE `movie` SET `trailer` = 'https://www.youtube.com/watch?v=xYQ65FNLJvU' WHERE `title` = '封神第二部：战火西岐';
UPDATE `movie` SET `trailer` = 'https://www.youtube.com/watch?v=EBLY_sez_LI' WHERE `title` = '哪吒之魔童闹海';
UPDATE `movie` SET `trailer` = 'https://www.youtube.com/watch?v=1nrBhJE2w7E' WHERE `title` = '满江红';

-- 演示影院管理员开箱即可管理影院 1；已有业务库的影院归属不由迁移猜测。
UPDATE `user` SET cinema_id = 1 WHERE username = 'cinema_admin';

-- 重建规则与领取计数，日期相对执行日，演示兑换码不会因固定年份过期。
INSERT INTO coupon_code
  (id,code,name,amount,min_spend,valid_days,max_redemptions,redeemed_count,valid_from,expire_time,status)
VALUES
  (1,'JINLI699','兑换码专享满 80 减 6.99 元券',6.99,80.00,7,10000,0,
   v_seed_today,DATE_ADD(v_seed_today,INTERVAL 1 YEAR),1);
INSERT INTO user_coupon (id,user_id,name,amount,min_spend,status,valid_from,expire_time)
VALUES (1,1,'新人观影券',10.00,40.00,0,v_seed_today,DATE_ADD(v_seed_today,INTERVAL 30 DAY));

-- 每厅每天四场：间隔 220 分钟，只排片长 60-200 分钟的已上映热映片，至少留 20 分钟清场。
-- 影院、容量、结束时间全部从真实关联数据获取，避免硬编码 ID 组合和放映时长。
INSERT INTO showtime
  (movie_id,cinema_id,hall_id,start_time,end_time,base_price,total_seats,available_seats,status)
WITH RECURSIVE dates (show_date) AS (
  SELECT v_seed_today
  UNION ALL SELECT DATE_ADD(show_date,INTERVAL 1 DAY) FROM dates
    WHERE show_date < DATE_ADD(v_seed_today,INTERVAL 10 DAY)
), slots AS (
  SELECT 1 AS slot_no,TIME('09:40:00') AS start_clock
  UNION ALL SELECT 2,TIME('13:20:00')
  UNION ALL SELECT 3,TIME('17:00:00')
  UNION ALL SELECT 4,TIME('20:40:00')
), eligible_movies AS (
  SELECT d.show_date,m.id AS movie_id,m.duration,
    ROW_NUMBER() OVER (PARTITION BY d.show_date ORDER BY m.id) AS movie_rank,
    COUNT(*) OVER (PARTITION BY d.show_date) AS movie_count
  FROM dates d JOIN movie m ON m.deleted=0 AND m.status=1
    AND m.release_date <= d.show_date AND m.duration BETWEEN 60 AND 200
), eligible_halls AS (
  SELECT h.id,h.cinema_id,h.name,h.total_seats,
    ROW_NUMBER() OVER (ORDER BY h.cinema_id,h.id) AS hall_rank
  FROM hall h JOIN cinema c ON c.id=h.cinema_id
  WHERE h.deleted=0 AND h.status=1 AND c.deleted=0 AND c.status=1
)
SELECT m.movie_id,h.cinema_id,h.id,
  TIMESTAMP(d.show_date,slot.start_clock),
  DATE_ADD(TIMESTAMP(d.show_date,slot.start_clock),INTERVAL m.duration MINUTE),
  CASE slot.slot_no WHEN 1 THEN 32.00 WHEN 2 THEN 39.00 WHEN 3 THEN 45.00 ELSE 49.00 END
    + CASE WHEN h.name LIKE '%VIP%' THEN 12.00
           WHEN h.name LIKE '%IMAX%' OR h.name LIKE '%巨幕%' THEN 10.00
           WHEN h.name LIKE '%杜比%' OR h.name LIKE '%激光%' THEN 6.00 ELSE 0.00 END
    + CASE WHEN DAYOFWEEK(d.show_date) IN (1,7) THEN 3.00 ELSE 0.00 END,
  h.total_seats,h.total_seats,0
FROM dates d CROSS JOIN slots slot CROSS JOIN eligible_halls h
JOIN eligible_movies m ON m.show_date=d.show_date
  AND m.movie_rank=MOD(DATEDIFF(d.show_date,v_seed_today)*7+(h.hall_rank-1)*4+slot.slot_no-1,m.movie_count)+1
WHERE TIMESTAMP(d.show_date,slot.start_clock) >= DATE_ADD(v_seed_now,INTERVAL 15 MINUTE);

IF NOT EXISTS (SELECT 1 FROM showtime) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Demo reset aborted: no eligible movie or hall for future showtimes';
END IF;
COMMIT;
DO RELEASE_LOCK(CONCAT('movie_demo:', MD5(DATABASE())));
SET v_lock_acquired = 0;
SELECT '演示数据导入完成；三个账号密码均为 admin123，影院管理员已绑定影院 1。' AS message;
SELECT MIN(DATE(start_time)) AS first_show_date,MAX(DATE(start_time)) AS last_show_date,
       COUNT(*) AS showtime_count FROM showtime;
END$$
DELIMITER ;
CALL `reset_movie_ticket_demo`();
DROP PROCEDURE `reset_movie_ticket_demo`;
