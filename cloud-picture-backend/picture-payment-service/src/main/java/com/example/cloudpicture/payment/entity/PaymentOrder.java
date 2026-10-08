package com.example.cloudpicture.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 支付订单：状态机 UNPAID → PAID / CANCELED / CLOSED；订单行只增不删（无逻辑删除） */
@Data
@TableName("t_payment_order")
public class PaymentOrder {

    public static final String STATUS_UNPAID = "UNPAID";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_CANCELED = "CANCELED";
    public static final String STATUS_CLOSED = "CLOSED";

    public static final String CHANNEL_MOCK = "MOCK";
    public static final String CHANNEL_ALIPAY = "ALIPAY";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 业务单号，同时也是支付宝 out_trade_no；uk_order_no 兜底唯一 */
    private String orderNo;

    private Long userId;

    /** 购买的目标档位：PRO/MAX */
    private String tier;

    /** 金额（分） */
    private Integer amount;

    private String status;

    /** MOCK / ALIPAY */
    private String channel;

    /** 渠道交易号（支付宝 trade_no），uk_transaction_id 构成支付幂等第一层 */
    private String transactionId;

    private LocalDateTime payTime;

    /** 订单超时时间，UNPAID 超过即关单 */
    private LocalDateTime expireTime;

    /** 套餐时长是否已发放：0 否 1 是；授权幂等标记，防止重复支付重复发放 */
    private Integer granted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}