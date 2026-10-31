-- 积分余额支持半积分精度：100 积分抵扣 1 元，消费 1 元获得 0.5 积分。
ALTER TABLE `user`
    ADD COLUMN `points` DECIMAL(12,1) NOT NULL DEFAULT 1280.0 COMMENT '可用积分' AFTER `status`;

ALTER TABLE `ticket_order`
    ADD COLUMN `points_used` DECIMAL(12,1) NOT NULL DEFAULT 0.0 COMMENT '本单抵扣积分' AFTER `discount_amount`,
    ADD COLUMN `points_earned` DECIMAL(12,1) NOT NULL DEFAULT 0.0 COMMENT '本单获得积分' AFTER `points_used`;
