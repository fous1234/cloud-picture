ALTER TABLE `t_image`
    ADD COLUMN `ai_review_verdict`    varchar(16)  NULL COMMENT 'AI 审核结论：PASS/REVIEW/BLOCK/ERROR/SKIP',
    ADD COLUMN `ai_review_confidence` int          NULL COMMENT 'AI 置信度 0-100',
    ADD COLUMN `ai_review_labels`     varchar(512) NULL COMMENT 'AI 命中标签，逗号分隔',
    ADD COLUMN `ai_review_time`       datetime     NULL COMMENT 'AI 审核时间';

-- 功能上线前的存量待审图一次性标记 SKIP（仅处理上线后新上传的裁定）：
-- 扫描条件为 ai_review_verdict IS NULL，历史积压继续纯人工
UPDATE `t_image` SET `ai_review_verdict` = 'SKIP' WHERE `review_status` = 0;
