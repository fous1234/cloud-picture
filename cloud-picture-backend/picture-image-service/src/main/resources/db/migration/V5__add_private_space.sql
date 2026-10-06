-- 私有空间：空间本身 + 私有图片（与 t_image 物理分表，结构性隔离）
CREATE TABLE IF NOT EXISTS `t_private_space`
(
    `id`          bigint      NOT NULL COMMENT '私有空间 id',
    `owner_id`    bigint      NOT NULL COMMENT '所属用户 id，一人一个空间',
    `name`        varchar(64) NOT NULL DEFAULT '我的私有空间' COMMENT '空间名称',
    `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_owner_id` (`owner_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='私有空间';

-- 无 is_delete：空间删除即物理删除，软删行会占住 uk_owner_id 挡住重建
CREATE TABLE IF NOT EXISTS `t_space_image`
(
    `id`           bigint       NOT NULL COMMENT '图片 id',
    `space_id`     bigint       NOT NULL COMMENT '所属私有空间 id',
    `owner_id`     bigint       NOT NULL COMMENT '上传用户 id（空间归属的冗余，单表即可完成权限校验）',
    `cos_key`      varchar(512) NOT NULL COMMENT 'COS 对象 Key',
    `name`         varchar(256) NOT NULL DEFAULT '' COMMENT '图片名称',
    `introduction` varchar(512)          DEFAULT NULL COMMENT '简介',
    `category`     varchar(64)           DEFAULT NULL COMMENT '分类',
    `tags`         varchar(512)          DEFAULT NULL COMMENT '标签，逗号分隔（纯字符串，不接 t_image_tag）',
    `pic_size`     bigint                DEFAULT NULL COMMENT '文件大小（字节）',
    `pic_width`    int                   DEFAULT NULL COMMENT '宽',
    `pic_height`   int                   DEFAULT NULL COMMENT '高',
    `pic_format`   varchar(32)           DEFAULT NULL COMMENT '格式',
    `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_delete`    tinyint      NOT NULL DEFAULT 0 COMMENT '是否删除：0 否 1 是',
    PRIMARY KEY (`id`),
    KEY `idx_space_id` (`space_id`),
    KEY `idx_owner_id` (`owner_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='私有空间图片';