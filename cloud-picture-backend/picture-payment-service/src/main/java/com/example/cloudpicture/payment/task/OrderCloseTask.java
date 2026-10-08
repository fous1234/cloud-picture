package com.example.cloudpicture.payment.task;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.cloudpicture.payment.entity.PaymentOrder;
import com.example.cloudpicture.payment.mapper.PaymentOrderMapper;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 订单超时关单：把过期未支付的订单置为 CLOSED；查询侧的懒关单是它的兜底 */
@Slf4j
@Service
public class OrderCloseTask {

    private final PaymentOrderMapper paymentOrderMapper;

    public OrderCloseTask(PaymentOrderMapper paymentOrderMapper) {
        this.paymentOrderMapper = paymentOrderMapper;
    }

    @Scheduled(cron = "${picture.payment.close-cron:0 * * * * ?}")
    public void closeExpiredOrders() {
        int rows = paymentOrderMapper.update(null, new LambdaUpdateWrapper<PaymentOrder>()
                .set(PaymentOrder::getStatus, PaymentOrder.STATUS_CLOSED)
                .eq(PaymentOrder::getStatus, PaymentOrder.STATUS_UNPAID)
                .le(PaymentOrder::getExpireTime, LocalDateTime.now()));
        if (rows > 0) {
            log.info("支付订单超时关单: count={}", rows);
        }
    }
}