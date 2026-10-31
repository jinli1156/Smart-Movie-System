-- 目录扩展影片曾错误复用原始片单海报，修正现有数据库中的全部映射。
UPDATE `movie`
SET `poster` = CASE `title`
  WHEN '星际穿越' THEN '/api/uploads/poster-latest-1889243.jpg'
  WHEN '盗梦空间' THEN '/api/uploads/poster-latest-3541415.jpg'
  WHEN '楚门的世界' THEN '/api/uploads/poster-latest-1292064.jpg'
  WHEN '千与千寻' THEN '/api/uploads/poster-latest-1291561.jpg'
  WHEN '疯狂动物城2' THEN '/api/uploads/poster-latest-34780991.jpg'
  WHEN '误杀3' THEN '/api/uploads/poster-latest-36704893.jpg'
  WHEN '熊出没·重启未来' THEN '/api/uploads/poster-latest-36712987.jpg'
  WHEN '雄狮少年2' THEN '/api/uploads/poster-latest-35883131.jpg'
  WHEN '海洋奇缘2' THEN '/api/uploads/poster-latest-34603753.jpg'
  WHEN '好东西' THEN '/api/uploads/poster-latest-36154853.jpg'
END
WHERE `title` IN (
  '星际穿越', '盗梦空间', '楚门的世界', '千与千寻', '疯狂动物城2',
  '误杀3', '熊出没·重启未来', '雄狮少年2', '海洋奇缘2', '好东西'
);
