package com.example.cloudpicture.payment.controller;

import com.example.cloudpicture.common.api.ApiResponse;
import com.example.cloudpicture.common.api.PageData;
import com.example.cloudpicture.payment.dto.request.CreateOrderRequest;
import com.example.cloudpicture.payment.dto.request.PaymentOrderQueryRequest;
import com.example.cloudpicture.payment.dto.response.OrderVO;
import com.example.cloudpicture.payment.dto.response.PlanListVO;
import com.example.cloudpicture.payment.service.PaymentOrderService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用户端支付：套餐目录、下单、支付入口与订单管理；所有接口都只能操作自己的订单 */
@Validated
@RestController
@RequestMapping("/payment")
public class PaymentController {

    private final PaymentOrderService paymentOrderService;

    public PaymentController(PaymentOrderService paymentOrderService) {
        this.paymentOrderService = paymentOrderService;
    }

    @GetMapping("/plans")
    public ApiResponse<PlanListVO> plans() {
        return ApiResponse.success(paymentOrderService.listPlans());
    }

    @PostMapping("/order")
    public ApiResponse<OrderVO> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.success(paymentOrderService.createOrder(request));
    }

    @PostMapping("/order/{orderNo}/pay")
    public ApiResponse<OrderVO> payAgain(@PathVariable("orderNo") String orderNo) {
        return ApiResponse.success(paymentOrderService.payAgain(orderNo));
    }

    @GetMapping("/order/{orderNo}")
    public ApiResponse<OrderVO> getOrder(@PathVariable("orderNo") String orderNo) {
        return ApiResponse.success(paymentOrderService.getOrder(orderNo));
    }

    @GetMapping("/orders")
    public ApiResponse<PageData<OrderVO>> myOrders(@Valid PaymentOrderQueryRequest request) {
        return ApiResponse.success(paymentOrderService.pageMyOrders(request));
    }

    @PostMapping("/order/{orderNo}/cancel")
    public ApiResponse<Boolean> cancel(@PathVariable("orderNo") String orderNo) {
        return ApiResponse.success(paymentOrderService.cancel(orderNo));
    }

    @PostMapping("/order/{orderNo}/mock-pay")
    public ApiResponse<Boolean> mockPay(@PathVariable("orderNo") String orderNo) {
        return ApiResponse.success(paymentOrderService.mockPay(orderNo));
    }

    /**
     * 支付宝异步通知：无登录态，由 picture.auth.public-requests 放行；
     * 应答必须是纯文本 success / failure，否则支付宝按策略重试
     */
    @PostMapping(value = "/notify/alipay", produces = MediaType.TEXT_PLAIN_VALUE)
    public String notifyAlipay(@RequestParam Map<String, String> params) {
        return paymentOrderService.handleNotify(params);
    }
}