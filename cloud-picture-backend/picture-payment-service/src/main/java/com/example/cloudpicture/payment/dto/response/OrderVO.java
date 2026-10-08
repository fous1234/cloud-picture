package com.example.cloudpicture.payment.dto.response;

import com.example.cloudpicture.common.tier.TierPlan;
import com.example.cloudpicture.payment.entity.PaymentOrder;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class OrderVO {

    private String orderNo;
    /** 管理端需要，用户端只用于展示自己的订单 */
    private Long userId;
    private String tier;
    private String tierName;
    /** 金额（分） */
    private Integer amountFen;
    private String status;
    private String channel;
    private String transactionId;
    private LocalDateTime payTime;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
    /** 生效后的套餐到期时间：仅单笔详情在已支付时回填 */
    private LocalDateTime tierExpireTime;
    /** 是否走内置模拟支付（下单/继续支付时返回） */
    private boolean mockPay;
    /** 支付宝跳转收银台表单（alipay 通道下单/继续支付时返回） */
    private String redirectForm;

    public static OrderVO from(PaymentOrder order, LocalDateTime tierExpireTime) {
        OrderVO vo = new OrderVO();
        vo.orderNo = order.getOrderNo();
        vo.userId = order.getUserId();
        vo.tier = order.getTier();
        vo.tierName = TierPlan.of(order.getTier()).getDisplayName();
        vo.amountFen = order.getAmount();
        vo.status = order.getStatus();
        vo.channel = order.getChannel();
        vo.transactionId = order.getTransactionId();
        vo.payTime = order.getPayTime();
        vo.expireTime = order.getExpireTime();
        vo.createTime = order.getCreateTime();
        vo.tierExpireTime = tierExpireTime;
        return vo;
    }
}