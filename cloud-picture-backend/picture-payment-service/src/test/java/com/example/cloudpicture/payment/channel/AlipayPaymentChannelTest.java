package com.example.cloudpicture.payment.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alipay.api.internal.util.AlipaySignature;
import com.example.cloudpicture.payment.config.PaymentProperties;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 用本地生成的 RSA2 密钥对走一遍真实验签路径：这是回调安全边界的唯一关口 */
class AlipayPaymentChannelTest {

    private static final String ORDER_NO = "PC20261007120345A7F2C1";
    private static final String TRADE_NO = "2026100722001459381023456789";

    private static String privateKey;
    private static String publicKey;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        KeyPair keyPair = newKeyPair();
        privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    @Test
    void acceptsNotifySignedByAlipayPublicKey() throws Exception {
        NotifyResult result = channel().verifyAndParseNotify(signedNotify(privateKey));

        assertTrue(result.isVerified(), result.getFailReason());
        assertTrue(result.isPaid());
        assertEquals(ORDER_NO, result.getOrderNo());
        assertEquals(TRADE_NO, result.getTransactionId());
        assertEquals(990, result.getAmountFen());
    }

    @Test
    void rejectsNotifyWhoseAmountWasTampered() throws Exception {
        Map<String, String> params = signedNotify(privateKey);
        params.put("total_amount", "0.01");

        assertFalse(channel().verifyAndParseNotify(params).isVerified());
    }

    @Test
    void rejectsNotifySignedByAnotherKey() throws Exception {
        String otherPrivateKey = Base64.getEncoder()
                .encodeToString(newKeyPair().getPrivate().getEncoded());

        assertFalse(channel().verifyAndParseNotify(signedNotify(otherPrivateKey)).isVerified());
    }

    @Test
    void treatsWaitingStatusAsNotPaidButVerified() throws Exception {
        Map<String, String> params = signedNotify(privateKey);
        params.put("trade_status", "WAIT_BUYER_PAY");
        resign(params, privateKey);

        NotifyResult result = channel().verifyAndParseNotify(params);

        assertTrue(result.isVerified(), result.getFailReason());
        assertFalse(result.isPaid());
    }

    @Test
    void convertsYuanToFenOnExactCents() {
        assertEquals(990, PaymentChannel.parseAmountFen("9.90"));
        assertEquals(1990, PaymentChannel.parseAmountFen("19.90"));
        assertEquals(100, PaymentChannel.parseAmountFen("1"));
        assertEquals(0, PaymentChannel.parseAmountFen(null));
        assertEquals(0, PaymentChannel.parseAmountFen(" "));
    }

    private static Map<String, String> signedNotify(String signingKey) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("out_trade_no", ORDER_NO);
        params.put("trade_no", TRADE_NO);
        params.put("total_amount", "9.90");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("charset", "UTF-8");
        resign(params, signingKey);
        return params;
    }

    /** getSignContent 会把已存在的 sign 一并算进去，重签前必须先摘掉 */
    private static void resign(Map<String, String> params, String signingKey) throws Exception {
        params.remove("sign");
        params.put("sign", AlipaySignature.rsaSign(
                AlipaySignature.getSignContent(params), signingKey, "UTF-8", "RSA2"));
    }

    private static AlipayPaymentChannel channel() {
        PaymentProperties properties = new PaymentProperties();
        properties.getAlipay().setAppId("2021000000000000");
        properties.getAlipay().setPrivateKey(privateKey);
        properties.getAlipay().setPublicKey(publicKey);
        properties.getAlipay().setGatewayUrl("https://openapi-sandbox.dl.alipaydev.com/gateway.do");
        properties.getAlipay().setNotifyUrl("http://localhost:8084/payment/notify/alipay");
        properties.getAlipay().setReturnUrl("http://localhost:5173/private-space/orders");
        return new AlipayPaymentChannel(properties);
    }

    private static KeyPair newKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}