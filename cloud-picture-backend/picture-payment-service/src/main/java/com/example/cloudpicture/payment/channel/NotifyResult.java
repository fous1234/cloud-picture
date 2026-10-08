package com.example.cloudpicture.payment.channel;

import lombok.Data;

@Data
public class NotifyResult {

    /** 验签是否通过；false 时其余字段无意义 */
    private boolean verified;

    /** 渠道是否表示支付成功 */
    private boolean paid;

    private String orderNo;

    private String transactionId;

    /** 渠道回传金额（分），用于与订单金额核对 */
    private int amountFen;

    private String failReason;

    public static NotifyResult failure(String reason) {
        NotifyResult result = new NotifyResult();
        result.failReason = reason;
        return result;
    }
}