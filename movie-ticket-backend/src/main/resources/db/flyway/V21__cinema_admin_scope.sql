-- Existing cinema administrators remain unassigned until a platform admin assigns them.
ALTER TABLE `user` ADD COLUMN cinema_id BIGINT NULL COMMENT '影院管理员所属影院';
ALTER TABLE `user` ADD INDEX idx_user_cinema_id (cinema_id);
ALTER TABLE `user` ADD CONSTRAINT fk_user_managed_cinema FOREIGN KEY (cinema_id) REFERENCES cinema(id);
