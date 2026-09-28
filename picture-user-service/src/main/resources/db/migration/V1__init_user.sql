CREATE TABLE IF NOT EXISTS `t_user`
(
    `id`            bigint       NOT NULL COMMENT '用户 id',
    `user_account`  varchar(256) NOT NULL COMMENT '登录账号',
    `user_password` varchar(512) NOT NULL COMMENT '密码哈希',
    `user_name`     varchar(256) NOT NULL DEFAULT '' COMMENT '昵称',
    `user_avatar`   varchar(1024)         DEFAULT NULL COMMENT '头像地址',
    `user_profile`  varchar(512)          DEFAULT NULL COMMENT '简介',
    `user_role`     varchar(64)  NOT NULL DEFAULT 'USER' COMMENT '角色：USER/ADMIN',
    `status`        tinyint      NOT NULL DEFAULT 1 COMMENT '账号状态：0 禁用 1 正常',
    `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_delete`     tinyint      NOT NULL DEFAULT 0 COMMENT '是否删除：0 否 1 是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_account` (`user_account`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户';