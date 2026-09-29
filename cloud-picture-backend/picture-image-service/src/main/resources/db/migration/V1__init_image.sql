CREATE TABLE IF NOT EXISTS `t_image`
(
    `id`             bigint       NOT NULL COMMENT '图片 id',
    `cos_key`        varchar(512) NOT NULL COMMENT 'COS 对象 Key，预览地址由后端生成短期签名 URL',
    `name`           varchar(256) NOT NULL DEFAULT '' COMMENT '图片名称',
    `introduction`   varchar(512)          DEFAULT NULL COMMENT '简介',
    `category`       varchar(64)           DEFAULT NULL COMMENT '分类',
    `tags`           varchar(512)          DEFAULT NULL COMMENT '标签，逗号分隔',
    `pic_size`       bigint                DEFAULT NULL COMMENT '文件大小（字节）',
    `pic_width`      int                   DEFAULT NULL COMMENT '宽',
    `pic_height`     int                   DEFAULT NULL COMMENT '高',
    `pic_format`     varchar(32)           DEFAULT NULL COMMENT '格式',
    `owner_id`       bigint       NOT NULL COMMENT '上传用户 id',
    `review_status`  tinyint      NOT NULL DEFAULT 0 COMMENT '审核状态：0 待审核 1 通过 2 拒绝',
    `review_message` varchar(512)          DEFAULT NULL COMMENT '审核信息',
    `reviewer_id`    bigint                DEFAULT NULL COMMENT '审核人 id',
    `review_time`    datetime              DEFAULT NULL COMMENT '审核时间',
    `create_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_delete`      tinyint      NOT NULL DEFAULT 0 COMMENT '是否删除：0 否 1 是',
    PRIMARY KEY (`id`),
    KEY `idx_owner_id` (`owner_id`),
    KEY `idx_review_status` (`review_status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='图片';

CREATE TABLE IF NOT EXISTS `t_image_tag`
(
    `id`         bigint      NOT NULL COMMENT '标签 id',
    `tag_name`   varchar(64) NOT NULL COMMENT '标签名',
    `use_count`  int         NOT NULL DEFAULT 0 COMMENT '使用次数',
    `is_delete`  tinyint     NOT NULL DEFAULT 0 COMMENT '是否删除：0 否 1 是',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tag_name` (`tag_name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='图片标签字典';