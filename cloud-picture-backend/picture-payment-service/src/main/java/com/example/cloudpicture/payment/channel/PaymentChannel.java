package com.example.cloudpicture.payment.channel;

import com.example.cloudpicture.payment.entity.PaymentOrder;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付通道：下单与回调解析各有差异，落库与授权逻辑两者共用（见 PaymentOrderService.markPaid）。
 * 通道由 picture.payment.channel 选择，同一时刻只有一个实现生效
 */
public interface PaymentChannel {

    /** 通道标识，落库到 t_payment_order.channel */
    String name();

    /** 下单：返回内置模拟支付标记或收银台跳转表单 */
    PayResult createPay(PaymentOrder order);

    /** 校验并解析渠道异步通知；验签失败一律 verified=false */
    NotifyResult verifyAndParseNotify(Map<String, String> params);

    /** 渠道回传的金额（元字符串）转分 */
    static int parseAmountFen(String yuan) {
        if (yuan == null || yuan.isBlank()) {
            return 0;
        }
        return new BigDecimal(yuan).movePointRight(2).intValueExact();
    }
}