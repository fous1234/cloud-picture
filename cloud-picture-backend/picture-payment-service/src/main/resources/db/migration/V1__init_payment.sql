-- 支付服务独立逻辑库（与 user / image 库隔离）
-- MySQL 唯一索引允许多个 NULL，故 transaction_id 可为空的唯一键成立——这是支付幂等的第一层
CREATE TABLE IF NOT EXISTS `t_payment_order`
(
    `id`             bigint      NOT NULL COMMENT '订单 id',
    `order_no`       varchar(64) NOT NULL COMMENT '业务单号（支付宝 out_trade_no）',
    `user_id`        bigint      NOT NULL COMMENT '下单用户 id',
    `tier`           varchar(16) NOT NULL COMMENT '购买的目标档位：PRO/MAX',
    `amount`         int         NOT NULL COMMENT '金额（单位：分）',
    `status`         varchar(16) NOT NULL DEFAULT 'UNPAID' COMMENT 'UNPAID 待支付 / PAID 已支付 / CANCELED 已取消 / CLOSED 超时关闭',
    `channel`        varchar(16) NOT NULL COMMENT 'MOCK/ALIPAY',
    `transaction_id` varchar(64) DEFAULT NULL COMMENT '渠道交易号（支付宝 trade_no），幂等键',
    `pay_time`       datetime    DEFAULT NULL COMMENT '支付完成时间',
    `expire_time`    datetime    DEFAULT NULL COMMENT '订单超时时间，UNPAID 超过即关单',
    `create_time`    datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    UNIQUE KEY `uk_transaction_id` (`transaction_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status_create` (`status`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='支付订单';