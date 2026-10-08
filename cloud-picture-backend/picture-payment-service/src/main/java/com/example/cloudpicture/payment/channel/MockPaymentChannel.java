package com.example.cloudpicture.payment.channel;

import com.example.cloudpicture.payment.entity.PaymentOrder;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 内置模拟支付：不出网、断网可演示，走与真实回调完全相同的下游落库与授权逻辑 */
@Component
@ConditionalOnProperty(name = "picture.payment.channel", havingValue = "mock", matchIfMissing = true)
public class MockPaymentChannel implements PaymentChannel {

    @Override
    public String name() {
        return PaymentOrder.CHANNEL_MOCK;
    }

    @Override
    public PayResult createPay(PaymentOrder order) {
        return PayResult.mock();
    }

    /** 无外部渠道可验签：仅用于本地联调通知链路，正式回调只走 alipay 实现 */
    @Override
    public NotifyResult verifyAndParseNotify(Map<String, String> params) {
        String orderNo = params.get("out_trade_no");
        if (orderNo == null || orderNo.isBlank()) {
            return NotifyResult.failure("缺少 out_trade_no");
        }
        NotifyResult result = new NotifyResult();
        result.setVerified(true);
        result.setOrderNo(orderNo);
        result.setTransactionId(params.get("trade_no"));
        result.setAmountFen(PaymentChannel.parseAmountFen(params.get("total_amount")));
        result.setPaid("TRADE_SUCCESS".equals(params.get("trade_status")));
        return result;
    }
}