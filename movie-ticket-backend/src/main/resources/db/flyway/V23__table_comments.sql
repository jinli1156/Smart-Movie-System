-- 补齐早期迁移建表时遗漏的表注释。
-- 仅修改数据字典中的注释元数据，不改变表结构、索引、约束和业务数据。

ALTER TABLE `assistant_conversation` COMMENT = 'AI助手会话表';
ALTER TABLE `assistant_preference` COMMENT = 'AI助手用户偏好表';
ALTER TABLE `assistant_checkout` COMMENT = 'AI助手订单确认卡表';

ALTER TABLE `coupon_code` COMMENT = '优惠券兑换码表';
ALTER TABLE `coupon_code_redemption` COMMENT = '优惠券兑换码领取记录表';

-- V8 迁移时用于归档孤立推荐行为，保留以备追溯。
ALTER TABLE `migration_orphan_user_movie_behavior` COMMENT = '迁移存档：V8 前孤立的用户电影行为数据';

-- Flyway 自身维护的迁移历史表，补充注释便于数据库文档展示。
ALTER TABLE `flyway_schema_history` COMMENT = 'Flyway数据库迁移历史表';
