-- 授权幂等标记：同一订单至多发放一次套餐时长。
-- 先 CAS 抢占（granted 0→1）再授权；授权失败把标记退回 0，渠道重试可补发；
-- 这样既杜绝"重复支付白送时长"，也保留失败重试能力
ALTER TABLE `t_payment_order`
    ADD COLUMN `granted` tinyint NOT NULL DEFAULT 0 COMMENT '套餐时长是否已发放：0 否 1 是';