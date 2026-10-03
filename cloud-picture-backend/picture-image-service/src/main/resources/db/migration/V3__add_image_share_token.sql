-- 图片分享链接：share_token 为 NULL 表示当前没有有效分享链接；唯一索引保证一个 token 只对应一张图片
ALTER TABLE `t_image`
    ADD COLUMN `share_token` varchar(64) DEFAULT NULL COMMENT '分享 token，NULL 表示无有效分享链接，重新生成即覆盖、撤销即置空',
    ADD UNIQUE KEY `uk_share_token` (`share_token`);