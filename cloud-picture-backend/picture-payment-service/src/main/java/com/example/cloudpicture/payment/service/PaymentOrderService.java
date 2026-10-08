package com.example.cloudpicture.payment.service;

import com.example.cloudpicture.common.api.PageData;
import com.example.cloudpicture.payment.dto.request.AdminOrderQueryRequest;
import com.example.cloudpicture.payment.dto.request.CreateOrderRequest;
import com.example.cloudpicture.payment.dto.request.PaymentOrderQueryRequest;
import com.example.cloudpicture.payment.dto.response.OrderVO;
import com.example.cloudpicture.payment.dto.response.PlanListVO;
import java.util.Map;

/** 支付订单：下单、支付入口、订单查询与状态机流转 */
public interface PaymentOrderService {

    /** 套餐目录 + 当前用户档位（驱动前端卡片三态） */
    PlanListVO listPlans();

    OrderVO createOrder(CreateOrderRequest request);

    /** 继续支付：复用同一订单，重新取支付入口 */
    OrderVO payAgain(String orderNo);

    OrderVO getOrder(String orderNo);

    PageData<OrderVO> pageMyOrders(PaymentOrderQueryRequest request);

    boolean cancel(String orderNo);

    /** 内置模拟支付：仅 MOCK 通道订单可用 */
    boolean mockPay(String orderNo);

    PageData<OrderVO> pageAllOrders(AdminOrderQueryRequest request);

    /** 支付成功落库并授权；渠道回调与模拟支付共用这一段下游逻辑 */
    boolean markPaid(String orderNo, String transactionId, int amountFen);

    /** 渠道异步通知入口，返回给渠道的纯文本应答（success / failure） */
    String handleNotify(Map<String, String> params);
}