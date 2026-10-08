-- 私有空间套餐档位：权益挂空间（一人一空间），配额在 image-service 的 upload() 里就地校验
-- 存量空间自动落到 FREE（200MB / 200 张），tier_expire_time 为 NULL 表示无到期时间
ALTER TABLE `t_private_space`
    ADD COLUMN `tier` varchar(16) NOT NULL DEFAULT 'FREE' COMMENT '套餐档位：FREE/PRO/MAX',
    ADD COLUMN `tier_expire_time` datetime DEFAULT NULL COMMENT '套餐到期时间；FREE 为 NULL';