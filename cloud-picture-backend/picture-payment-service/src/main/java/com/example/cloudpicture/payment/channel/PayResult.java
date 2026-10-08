package com.example.cloudpicture.payment.channel;

import lombok.Data;

@Data
public class PayResult {

    /** 内置模拟支付：前端弹窗点"模拟支付成功" */
    private boolean mockPay;

    /** 收银台跳转表单 HTML（支付宝网页支付返回的是自动提交表单） */
    private String redirectForm;

    public static PayResult mock() {
        PayResult result = new PayResult();
        result.mockPay = true;
        return result;
    }

    public static PayResult form(String redirectForm) {
        PayResult result = new PayResult();
        result.redirectForm = redirectForm;
        return result;
    }
}