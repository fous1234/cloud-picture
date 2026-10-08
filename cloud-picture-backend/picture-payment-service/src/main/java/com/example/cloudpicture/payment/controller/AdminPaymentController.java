package com.example.cloudpicture.payment.controller;

import com.example.cloudpicture.common.api.ApiResponse;
import com.example.cloudpicture.common.api.PageData;
import com.example.cloudpicture.common.security.annotation.RequireRole;
import com.example.cloudpicture.common.security.context.CurrentUser;
import com.example.cloudpicture.payment.dto.request.AdminOrderQueryRequest;
import com.example.cloudpicture.payment.dto.response.OrderVO;
import com.example.cloudpicture.payment.service.PaymentOrderService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端订单：只读查看，不提供退款 / 关单 / 改套餐 */
@Validated
@RestController
@RequestMapping("/admin/payment")
@RequireRole(CurrentUser.ROLE_ADMIN)
public class AdminPaymentController {

    private final PaymentOrderService paymentOrderService;

    public AdminPaymentController(PaymentOrderService paymentOrderService) {
        this.paymentOrderService = paymentOrderService;
    }

    @GetMapping("/order/list")
    public ApiResponse<PageData<OrderVO>> listOrders(@Valid AdminOrderQueryRequest request) {
        return ApiResponse.success(paymentOrderService.pageAllOrders(request));
    }
}