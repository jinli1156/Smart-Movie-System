-- 为演示目录中的影院补齐可直接展示的门店图片；管理员已配置的图片不覆盖。
UPDATE `cinema`
SET `images` = CASE `id`
  WHEN 1 THEN 'https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=900&q=82'
  WHEN 2 THEN 'https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?auto=format&fit=crop&w=900&q=82'
  WHEN 3 THEN 'https://images.unsplash.com/photo-1485846234645-a62644f84728?auto=format&fit=crop&w=900&q=82'
  WHEN 4 THEN 'https://images.unsplash.com/photo-1574267432553-4b4628081c31?auto=format&fit=crop&w=900&q=82'
  WHEN 5 THEN 'https://images.unsplash.com/photo-1598899134739-24c46f58b8c0?auto=format&fit=crop&w=900&q=82'
  WHEN 6 THEN 'https://images.unsplash.com/photo-1586899028174-e7098604235b?auto=format&fit=crop&w=900&q=82'
  WHEN 7 THEN 'https://images.unsplash.com/photo-1543536448-d209d2d13a1c?auto=format&fit=crop&w=900&q=82'
  WHEN 8 THEN 'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=900&q=82'
  WHEN 9 THEN 'https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=900&q=82'
  WHEN 10 THEN 'https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?auto=format&fit=crop&w=900&q=82'
END
WHERE `deleted` = 0
  AND `id` BETWEEN 1 AND 10
  AND (`images` IS NULL OR TRIM(`images`) = '');
