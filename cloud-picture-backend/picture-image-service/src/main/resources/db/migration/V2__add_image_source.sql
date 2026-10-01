-- 图源导入：图片增加来源与署名信息（Pexels），保留逻辑删除语义，删除后可重新导入
ALTER TABLE `t_image`
    ADD COLUMN `source`           varchar(16)  NOT NULL DEFAULT 'LOCAL' COMMENT '来源：LOCAL 本地上传 / PEXELS 导入',
    ADD COLUMN `source_id`        varchar(64)           DEFAULT NULL COMMENT '来源平台图片 ID（Pexels），去重键',
    ADD COLUMN `source_page_url`  varchar(512)          DEFAULT NULL COMMENT '来源图片详情页地址',
    ADD COLUMN `photographer`     varchar(128)          DEFAULT NULL COMMENT '摄影师名称',
    ADD COLUMN `photographer_url` varchar(512)          DEFAULT NULL COMMENT '摄影师主页地址',
    ADD KEY `idx_source_source_id` (`source`, `source_id`);
