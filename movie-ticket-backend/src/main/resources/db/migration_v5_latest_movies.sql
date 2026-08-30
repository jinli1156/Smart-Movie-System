-- 2026-08-30：补充北京院线近期上映及即将上映的 20 部影片。
-- 按“片名 + 上映日期”去重，可安全重复执行。
USE `movie_ticket`;

INSERT INTO `movie`
(`title`, `english_title`, `director`, `actors`, `genre`, `country`, `language`, `duration`, `release_date`, `poster`, `description`, `rating`, `status`)
SELECT src.`title`, src.`english_title`, src.`director`, src.`actors`, src.`genre`, src.`country`, src.`language`, src.`duration`, src.`release_date`, src.`poster`, src.`description`, src.`rating`, src.`status`
FROM (
  SELECT '奥德赛' AS `title`, 'The Odyssey' AS `english_title`, '克里斯托弗·诺兰' AS `director`, '马特·达蒙,汤姆·霍兰德,安妮·海瑟薇,罗伯特·帕丁森,查理兹·塞隆' AS `actors`, '动作,历史,奇幻,冒险' AS `genre`, '美国,加拿大,冰岛,新西兰,摩洛哥,英国' AS `country`, '英语,希腊语' AS `language`, 173 AS `duration`, '2026-08-14' AS `release_date`, '/api/uploads/poster-latest-36808876.jpg' AS `poster`, '改编自荷马史诗，讲述奥德修斯在特洛伊战争结束后历经十年艰险返回故乡的传奇旅程。' AS `description`, 8.5 AS `rating`, 1 AS `status`
  UNION ALL SELECT '欢迎来龙餐馆', '', '文牧野', '沈腾,蒋奇明,奥马尔·谢里夫,李治廷,谢里夫·萨比', '剧情,战争', '中国大陆', '汉语普通话,阿拉伯语,英语', 140, '2026-08-11', '/api/uploads/poster-latest-35811064.jpg', '两名中国人在中东经营餐馆，战争突然爆发，他们必须在维系餐馆的同时面对命运与人性的考验。', 8.7, 1
  UNION ALL SELECT '空枪', '', '韩延', '朱一龙,檀健次,梁家辉,卫诗雅,惠英红', '剧情,犯罪', '中国大陆,中国香港', '粤语,汉语普通话,英语', 160, '2026-08-19', '/api/uploads/poster-latest-37508847.jpg', '九十年代的欲望之城中，一场巨款劫案牵动犯罪者、富商与两地警方，正邪角力逐渐失控。', 7.2, 1
  UNION ALL SELECT '肖申克的救赎', 'The Shawshank Redemption', '弗兰克·德拉邦特', '蒂姆·罗宾斯,摩根·弗里曼,鲍勃·冈顿,威廉·赛德勒,克兰西·布朗', '剧情,犯罪', '美国', '英语', 142, '2026-08-28', '/api/uploads/poster-latest-1292052.jpg', '蒙冤入狱的银行家安迪在肖申克监狱中坚守希望，并用漫长而周密的计划完成自我救赎。', 9.7, 1
  UNION ALL SELECT '数到三', '', '孙琳', '惠英红,张宥浩,潘斌龙,李健,黄子弘凡', '剧情', '中国大陆', '汉语普通话', 107, '2026-08-28', '/api/uploads/poster-latest-36931140.jpg', '失去儿子的母亲与一名接受心脏移植的青年相遇，温情背后却隐藏着经过精心设计的秘密。', 0.0, 1
  UNION ALL SELECT '牛来', '', '信雨萌', '信雨萌,孙丽芳', '动画', '中国大陆', '汉语普通话', 86, '2026-08-05', '/api/uploads/poster-latest-38581618.jpg', '一只小牛犊在多重力量的感召下成长为勇敢担当、直面生死的勇士。', 6.0, 1
  UNION ALL SELECT '密档', '', '郑大圣', '袁弘,李妍锡,邵峰,谢承颖,李晓川', '剧情,悬疑', '中国大陆', '汉语普通话', 132, '2026-08-28', '/api/uploads/poster-latest-35209727.jpg', '一九四二年的上海，一对地下党员夫妇隐于市井，在危机四伏的深宅中守护重要机要卷宗。', 0.0, 1
  UNION ALL SELECT '痴迷', 'Obsession', '库里·巴克', '迈克尔·约翰斯顿,印达·纳瓦雷特,库珀·汤姆林森,梅根·劳利斯,安迪·里克特', '恐怖,奇幻', '美国', '英语', 110, '2026-07-24', '/api/uploads/poster-latest-37450627.jpg', '青年借助禁忌传说实现爱情愿望，却发现圆满的感情正在召来蚕食现实的古老存在。', 7.6, 1
  UNION ALL SELECT '蜘蛛侠：崭新之日', 'Spider-Man: Brand New Day', '德斯汀·丹尼尔·克雷顿', '汤姆·霍兰德,赞达亚,萨迪·辛克,雅各布·贝塔隆,马克·鲁法洛', '动作,科幻,冒险', '美国,加拿大,英国,德国', '英语', 145, '2026-07-29', '/api/uploads/poster-latest-36246195.jpg', '被世界遗忘的彼得·帕克独自守护纽约，同时还要面对身体异变与多方邪恶势力带来的全新危机。', 7.8, 1
  UNION ALL SELECT '大唐妖探', '', '程腾,黄珉', '雷淞然,张呈,蔡海婷,张喆,王凯', '喜剧,动画,悬疑', '中国大陆', '汉语普通话', 117, '2026-08-22', '/api/uploads/poster-latest-35464353.jpg', '天才少年狄少与狼妖捕快阿萨被迫组队，在人妖共居的奇幻长安追查一桩离奇命案。', 0.0, 1
  UNION ALL SELECT '八仙！', '', '牟正洋', '陈浩,李绍哲,立冬,果子哥哥,董天弋', '喜剧,动画,奇幻', '中国大陆', '汉语普通话', 144, '2026-07-18', '/api/uploads/poster-latest-36882191.jpg', '八位身份各异的凡人误打误撞组成八仙团队，在笑闹旅程中共同面对一场惊天阴谋。', 8.2, 1
  UNION ALL SELECT '抓特务', '', '冯小刚', '雷佳音,胡歌,啜妮,张瑶,林晓凡', '剧情', '中国大陆', '汉语普通话', 141, '2026-06-19', '/api/uploads/poster-latest-36812879.jpg', '新中国成立初期，一名派出所所长搬进胡同追查潜伏特务，两人的较量牵动两个家庭数十年的命运。', 7.4, 1
  UNION ALL SELECT '机器人总动员', 'WALL·E', '安德鲁·斯坦顿', '本·贝尔特,艾丽莎·奈特,杰夫·格尔林,佛莱德·威拉特,西格妮·韦弗', '科幻,动画,冒险', '美国', '英语', 98, '2026-08-19', '/api/uploads/poster-latest-2131459.jpg', '被遗留在地球清理垃圾的机器人瓦力遇见探测机器人伊芙，一场跨越宇宙的冒险由此展开。', 9.3, 1
  UNION ALL SELECT '年会不能停！2', '', '董润年', '张若昀,白客,高叶,大鹏,庄达菲', '喜剧', '中国大陆', '汉语普通话', 125, '2026-08-01', '/api/uploads/poster-latest-36850814.jpg', '两名职场倒霉蛋陷入失败便会时间倒流的循环，在一次次重来中追查公司背后的真相。', 6.6, 1
  UNION ALL SELECT '去你的岛', '', '周浩然', '杨茜云,张福正,孙婉瑜,白望文一,钱琛', '爱情,动画,奇幻', '中国大陆', '汉语普通话', 119, '2026-08-07', '/api/uploads/poster-latest-35275131.jpg', '女孩进入一座由前男友记忆构成的奇幻岛屿，在失忆兔子的陪伴下重新理解爱与分离。', 7.6, 1
  UNION ALL SELECT '杀死比尔：血色全传', 'Kill Bill: The Whole Bloody Affair', '昆汀·塔伦蒂诺', '乌玛·瑟曼,刘玉玲,薇薇卡·福克斯,达丽尔·汉纳,迈克尔·马德森', '动作,犯罪', '美国', '英语', 276, '2026-09-04', '/api/uploads/poster-latest-10756537.jpg', '遭暗杀后昏迷多年的新娘醒来，踏上寻找昔日同僚与幕后首领的血色复仇之路。', 8.7, 2
  UNION ALL SELECT '坠落2：死点', 'Fall 2: Deadpoint', '迈克尔·斯派瑞,彼得·斯派瑞', '哈丽特·斯莱特,阿塞玛·托马斯,维吉尼亚·加德纳,格雷斯·卡罗琳·柯里,亚当·罗斯', '惊悚,冒险', '美国,英国', '英语', 98, '2026-09-04', '/api/uploads/poster-latest-36318704.jpg', '两名极限爱好者在高空栈道遭遇地震与暴雨，只能依靠有限物资寻找绝境生路。', 0.0, 2
  UNION ALL SELECT '怒之杀', 'Mutiny', '让-弗朗索瓦·雷切', '杰森·斯坦森,安娜贝拉·沃丽丝,阿纳斯·费达拉维修斯,艾德里安·莱斯特,罗兰·默勒', '动作,惊悚,犯罪', '英国,美国', '英语', 96, '2026-09-04', '/api/uploads/poster-latest-36889088.jpg', '贴身保镖被诬陷杀害雇主，他闯入远洋货轮展开复仇，并揭开隐藏在事件背后的阴谋。', 0.0, 2
  UNION ALL SELECT '怪屋', '', '王博伦', '傅菁,思漩,苏劲源,张越宁,王健宇', '悬疑,惊悚,恐怖', '中国大陆', '汉语普通话', 107, '2026-09-04', '/api/uploads/poster-latest-37427091.jpg', '凶宅试睡员与菜鸟博主入住法拍别墅，诡异声响、血印与空间错乱接连出现。', 0.0, 2
  UNION ALL SELECT '燃烧吧！爸爸', '', '刘潇阳', '文淇,倪虹洁,喻恩泰,郭俊辰,葛鑫怡', '剧情,喜剧', '中国大陆', '汉语普通话,沪语', 102, '2026-09-05', '/api/uploads/poster-latest-36903929.jpg', '一对性格迥异的母女在处理父亲身后事的过程中，逐渐发现他的另一面并重新理解彼此。', 0.0, 2
) AS src
WHERE NOT EXISTS (
  SELECT 1 FROM `movie` existing
  WHERE existing.`title` = src.`title`
    AND existing.`release_date` = src.`release_date`
);
