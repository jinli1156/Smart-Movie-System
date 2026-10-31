-- 将个人中心的演示钱包余额持久化，统一由用户资料接口返回。
ALTER TABLE `user`
    ADD COLUMN `wallet_balance` DECIMAL(12,2) NOT NULL DEFAULT 256.00 COMMENT '钱包可用余额' AFTER `points`;
