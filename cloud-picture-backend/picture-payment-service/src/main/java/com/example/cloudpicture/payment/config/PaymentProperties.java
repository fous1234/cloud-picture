package com.example.cloudpicture.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 支付相关配置：支付通道与支付宝沙箱参数；密钥只来自环境变量，不落库、不打印 */
@Data
@Component
@ConfigurationProperties(prefix = "picture")
public class PaymentProperties {

    private Payment payment = new Payment();
    private Alipay alipay = new Alipay();

    @Data
    public static class Payment {
        /** mock / alipay */
        private String channel = "mock";
        private int orderTimeoutMinutes = 30;
    }

    @Data
    public static class Alipay {
        private String appId;
        private String privateKey;
        /** 支付宝公钥，验签用（不是应用公钥） */
        private String publicKey;
        private String gatewayUrl;
        private String notifyUrl;
        private String returnUrl;
    }
}