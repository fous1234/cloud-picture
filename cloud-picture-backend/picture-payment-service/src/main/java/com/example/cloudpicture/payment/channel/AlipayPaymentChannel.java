package com.example.cloudpicture.payment.channel;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import com.example.cloudpicture.common.tier.TierPlan;
import com.example.cloudpicture.payment.config.PaymentProperties;
import com.example.cloudpicture.payment.entity.PaymentOrder;
import java.math.BigDecimal;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 支付宝沙箱（电脑网站支付）：下单返回自动提交表单，回调按支付宝公钥 RSA2 验签。
 * 金额内部一律"分"，只在传给支付宝时转"元"字符串
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "picture.payment.channel", havingValue = "alipay")
public class AlipayPaymentChannel implements PaymentChannel {

    private static final String SIGN_TYPE = "RSA2";
    private static final String CHARSET = "UTF-8";
    private static final String FORMAT = "json";
    private static final String PRODUCT_CODE = "FAST_INSTANT_TRADE_PAY";
    private static final String TRADE_SUCCESS = "TRADE_SUCCESS";
    private static final String TRADE_FINISHED = "TRADE_FINISHED";

    private final PaymentProperties properties;
    private final AlipayClient alipayClient;

    public AlipayPaymentChannel(PaymentProperties properties) {
        this.properties = properties;
        PaymentProperties.Alipay alipay = properties.getAlipay();
        this.alipayClient = new DefaultAlipayClient(alipay.getGatewayUrl(), alipay.getAppId(),
                alipay.getPrivateKey(), FORMAT, CHARSET, alipay.getPublicKey(), SIGN_TYPE);
    }

    @Override
    public String name() {
        return PaymentOrder.CHANNEL_ALIPAY;
    }

    @Override
    public PayResult createPay(PaymentOrder order) {
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(properties.getAlipay().getNotifyUrl());
        request.setReturnUrl(properties.getAlipay().getReturnUrl());
        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        model.setOutTradeNo(order.getOrderNo());
        model.setTotalAmount(yuan(order.getAmount()));
        model.setSubject("私有空间 " + TierPlan.of(order.getTier()).getDisplayName() + " 套餐 30 天");
        model.setProductCode(PRODUCT_CODE);
        request.setBizModel(model);
        try {
            return PayResult.form(alipayClient.pageExecute(request, "POST").getBody());
        } catch (AlipayApiException e) {
            log.error("支付宝下单失败: orderNo={}", order.getOrderNo(), e);
            throw new BusinessException(ErrorCode.PAY_CHANNEL_ERROR, "支付宝下单失败");
        }
    }

    @Override
    public NotifyResult verifyAndParseNotify(Map<String, String> params) {
        try {
            if (!AlipaySignature.rsaCheckV2(params, properties.getAlipay().getPublicKey(), CHARSET, SIGN_TYPE)) {
                return NotifyResult.failure("验签失败");
            }
        } catch (AlipayApiException e) {
            return NotifyResult.failure("验签异常");
        }
        String orderNo = params.get("out_trade_no");
        if (orderNo == null || orderNo.isBlank()) {
            return NotifyResult.failure("缺少 out_trade_no");
        }
        NotifyResult result = new NotifyResult();
        result.setVerified(true);
        result.setOrderNo(orderNo);
        result.setTransactionId(params.get("trade_no"));
        result.setAmountFen(PaymentChannel.parseAmountFen(params.get("total_amount")));
        String tradeStatus = params.get("trade_status");
        result.setPaid(TRADE_SUCCESS.equals(tradeStatus) || TRADE_FINISHED.equals(tradeStatus));
        return result;
    }

    private static String yuan(int fen) {
        return BigDecimal.valueOf(fen).movePointLeft(2).toPlainString();
    }
}