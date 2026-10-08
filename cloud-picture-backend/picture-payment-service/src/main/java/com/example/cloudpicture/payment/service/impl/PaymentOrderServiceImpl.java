package com.example.cloudpicture.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.cloudpicture.common.api.PageData;
import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import com.example.cloudpicture.common.security.context.CurrentUser;
import com.example.cloudpicture.common.tier.TierPlan;
import com.example.cloudpicture.payment.channel.NotifyResult;
import com.example.cloudpicture.payment.channel.PayResult;
import com.example.cloudpicture.payment.channel.PaymentChannel;
import com.example.cloudpicture.payment.client.ImageSpaceClient;
import com.example.cloudpicture.payment.config.PaymentProperties;
import com.example.cloudpicture.payment.dto.request.AdminOrderQueryRequest;
import com.example.cloudpicture.payment.dto.request.CreateOrderRequest;
import com.example.cloudpicture.payment.dto.request.PaymentOrderQueryRequest;
import com.example.cloudpicture.payment.dto.request.SpaceGrantPayload;
import com.example.cloudpicture.payment.dto.response.OrderVO;
import com.example.cloudpicture.payment.dto.response.PlanListVO;
import com.example.cloudpicture.payment.dto.response.PlanVO;
import com.example.cloudpicture.payment.dto.response.SpaceStateVO;
import com.example.cloudpicture.payment.entity.PaymentOrder;
import com.example.cloudpicture.payment.mapper.PaymentOrderMapper;
import com.example.cloudpicture.payment.service.PaymentOrderService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class PaymentOrderServiceImpl implements PaymentOrderService {

    /** 订阅周期天数，与套餐展示口径一致 */
    private static final int PLAN_DAYS = 30;
    private static final String BADGE_CURRENT = "当前套餐";
    private static final String BADGE_RECOMMEND = "推荐";
    private static final int GRANTED = 1;
    private static final int NOT_GRANTED = 0;
    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PaymentOrderMapper paymentOrderMapper;
    private final ImageSpaceClient imageSpaceClient;
    private final PaymentChannel paymentChannel;
    private final PaymentProperties properties;

    public PaymentOrderServiceImpl(PaymentOrderMapper paymentOrderMapper, ImageSpaceClient imageSpaceClient,
                                   PaymentChannel paymentChannel, PaymentProperties properties) {
        this.paymentOrderMapper = paymentOrderMapper;
        this.imageSpaceClient = imageSpaceClient;
        this.paymentChannel = paymentChannel;
        this.properties = properties;
    }

    @Override
    public PlanListVO listPlans() {
        SpaceStateVO space = imageSpaceClient.mine(CurrentUser.get().getId()).getData();
        TierPlan current = space == null ? TierPlan.FREE : TierPlan.of(space.getTier());

        PlanListVO result = new PlanListVO();
        result.setSpaceCreated(space != null);
        result.setCurrentTier(current.name());
        result.setCurrentTierName(current.getDisplayName());
        result.setExpireTime(space == null ? null : space.getTierExpireTime());

        List<PlanVO> plans = new ArrayList<>();
        for (TierPlan plan : TierPlan.values()) {
            PlanVO vo = new PlanVO();
            vo.setTier(plan.name());
            vo.setName(plan.getDisplayName());
            vo.setImageLimit(plan.getImageLimit());
            vo.setSizeLimitBytes(plan.getSizeLimitBytes());
            vo.setPriceFen(plan.getPriceFen());
            vo.setPlanDays(PLAN_DAYS);
            vo.setCurrent(plan == current);
            // 只支持向上购买或同档续费；未创建空间时一律不可购买
            vo.setPurchasable(space != null && plan.isPurchasable() && plan.ordinal() >= current.ordinal());
            vo.setBadge(plan == current ? BADGE_CURRENT : (plan == TierPlan.PRO ? BADGE_RECOMMEND : null));
            plans.add(vo);
        }
        result.setPlans(plans);
        return result;
    }

    @Override
    public OrderVO createOrder(CreateOrderRequest request) {
        Long userId = CurrentUser.get().getId();
        TierPlan target = TierPlan.of(request.getTier());
        if (!target.isPurchasable()) {
            throw new BusinessException(ErrorCode.PLAN_NOT_PURCHASABLE, "普通档位无需购买");
        }
        SpaceStateVO space = imageSpaceClient.mine(userId).getData();
        if (space == null) {
            throw new BusinessException(ErrorCode.PLAN_NOT_PURCHASABLE, "请先创建私有空间再升级套餐");
        }
        if (target.ordinal() < TierPlan.of(space.getTier()).ordinal()) {
            throw new BusinessException(ErrorCode.PLAN_NOT_PURCHASABLE, "只支持向上购买或同档续费");
        }
        PaymentOrder order = new PaymentOrder();
        order.setOrderNo(nextOrderNo());
        order.setUserId(userId);
        order.setTier(target.name());
        order.setAmount(target.getPriceFen());
        order.setStatus(PaymentOrder.STATUS_UNPAID);
        order.setChannel(paymentChannel.name());
        order.setExpireTime(LocalDateTime.now().plusMinutes(properties.getPayment().getOrderTimeoutMinutes()));
        paymentOrderMapper.insert(order);
        return withPayEntry(order);
    }

    @Override
    public OrderVO payAgain(String orderNo) {
        PaymentOrder order = requireMyOrder(orderNo);
        refreshIfExpired(order);
        requireUnpaid(order, "订单状态不允许继续支付");
        return withPayEntry(order);
    }

    @Override
    public OrderVO getOrder(String orderNo) {
        PaymentOrder order = requireMyOrder(orderNo);
        refreshIfExpired(order);
        LocalDateTime tierExpireTime = null;
        if (PaymentOrder.STATUS_PAID.equals(order.getStatus())) {
            SpaceStateVO space = imageSpaceClient.mine(order.getUserId()).getData();
            tierExpireTime = space == null ? null : space.getTierExpireTime();
        }
        return OrderVO.from(order, tierExpireTime);
    }

    @Override
    public PageData<OrderVO> pageMyOrders(PaymentOrderQueryRequest request) {
        Long userId = CurrentUser.get().getId();
        Page<PaymentOrder> page = paymentOrderMapper.selectPage(Page.of(request.getCurrent(), request.getSize()),
                new LambdaQueryWrapper<PaymentOrder>()
                        .eq(PaymentOrder::getUserId, userId)
                        .eq(StringUtils.hasText(request.getStatus()), PaymentOrder::getStatus, request.getStatus())
                        .orderByDesc(PaymentOrder::getCreateTime));
        // 列表页顺带懒关单：只对确实过期的行做一次 CAS 写，通常为 0 行
        page.getRecords().forEach(this::refreshIfExpired);
        return toPage(page);
    }

    @Override
    public boolean cancel(String orderNo) {
        PaymentOrder order = requireMyOrder(orderNo);
        refreshIfExpired(order);
        requireUnpaid(order, "只有待支付订单可以取消");
        paymentOrderMapper.update(null, new LambdaUpdateWrapper<PaymentOrder>()
                .set(PaymentOrder::getStatus, PaymentOrder.STATUS_CANCELED)
                .eq(PaymentOrder::getOrderNo, orderNo)
                .eq(PaymentOrder::getStatus, PaymentOrder.STATUS_UNPAID));
        return true;
    }

    @Override
    public boolean mockPay(String orderNo) {
        PaymentOrder order = requireMyOrder(orderNo);
        if (!PaymentOrder.CHANNEL_MOCK.equals(order.getChannel())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "当前支付通道不是模拟支付");
        }
        return markPaid(orderNo, "MOCK" + orderNo, order.getAmount());
    }

    @Override
    public PageData<OrderVO> pageAllOrders(AdminOrderQueryRequest request) {
        Page<PaymentOrder> page = paymentOrderMapper.selectPage(Page.of(request.getCurrent(), request.getSize()),
                new LambdaQueryWrapper<PaymentOrder>()
                        .eq(request.getUserId() != null, PaymentOrder::getUserId, request.getUserId())
                        .eq(StringUtils.hasText(request.getStatus()), PaymentOrder::getStatus, request.getStatus())
                        .eq(StringUtils.hasText(request.getTier()), PaymentOrder::getTier, request.getTier())
                        .orderByDesc(PaymentOrder::getCreateTime));
        return toPage(page);
    }

    /**
     * 支付成功：CAS 把 UNPAID 翻成 PAID（配合 uk_transaction_id 构成幂等），随后授权。
     * 授权用 granted 标记做"至多一次"：先 CAS 抢占 0→1 再发放，抢不到就不发放——
     * 否则重复调用（用户反复点模拟支付、渠道重放通知）会每次再送 30 天。
     * 授权失败会把标记退回 0 并抛出，渠道重试时可补发；PAID 状态本身不回滚（钱已收）
     */
    @Override
    public boolean markPaid(String orderNo, String transactionId, int amountFen) {
        PaymentOrder order = requireOrder(orderNo);
        if (PaymentOrder.STATUS_CANCELED.equals(order.getStatus())
                || PaymentOrder.STATUS_CLOSED.equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "订单已取消或已关闭");
        }
        if (!PaymentOrder.STATUS_PAID.equals(order.getStatus())) {
            if (amountFen != order.getAmount()) {
                throw new BusinessException(ErrorCode.PAY_CHANNEL_ERROR, "支付金额与订单金额不一致");
            }
            paymentOrderMapper.update(null, new LambdaUpdateWrapper<PaymentOrder>()
                    .set(PaymentOrder::getStatus, PaymentOrder.STATUS_PAID)
                    .set(PaymentOrder::getTransactionId, transactionId)
                    .set(PaymentOrder::getPayTime, LocalDateTime.now())
                    .eq(PaymentOrder::getOrderNo, orderNo)
                    .eq(PaymentOrder::getStatus, PaymentOrder.STATUS_UNPAID));
        }
        grantOnce(order);
        return true;
    }

    /** 抢占授权标记成功才发放；发放失败退回标记，保证"至多一次"且失败可重试 */
    private void grantOnce(PaymentOrder order) {
        int claimed = paymentOrderMapper.update(null, new LambdaUpdateWrapper<PaymentOrder>()
                .set(PaymentOrder::getGranted, GRANTED)
                .eq(PaymentOrder::getOrderNo, order.getOrderNo())
                .eq(PaymentOrder::getGranted, NOT_GRANTED));
        if (claimed == 0) {
            log.debug("订单套餐已发放过，跳过重复授权: orderNo={}", order.getOrderNo());
            return;
        }
        try {
            grantPlan(order);
        } catch (RuntimeException e) {
            paymentOrderMapper.update(null, new LambdaUpdateWrapper<PaymentOrder>()
                    .set(PaymentOrder::getGranted, NOT_GRANTED)
                    .eq(PaymentOrder::getOrderNo, order.getOrderNo()));
            log.error("套餐授权失败，标记已退回待重试: orderNo={}", order.getOrderNo(), e);
            throw e;
        }
    }

    @Override
    public String handleNotify(Map<String, String> params) {
        NotifyResult notify = paymentChannel.verifyAndParseNotify(params);
        if (!notify.isVerified()) {
            log.warn("支付通知验签失败: reason={}", notify.getFailReason());
            return "failure";
        }
        if (!notify.isPaid()) {
            // 非成功状态（如等待付款）按已接收处理，避免渠道无谓重试
            return "success";
        }
        try {
            markPaid(notify.getOrderNo(), notify.getTransactionId(), notify.getAmountFen());
            return "success";
        } catch (RuntimeException e) {
            log.error("支付通知处理失败，等待渠道重试: orderNo={}", notify.getOrderNo(), e);
            return "failure";
        }
    }

    /** 授权：续费顺延公式落在本侧——未到期的在原到期时间上再加 30 天，FREE 或已到期从此刻起算 */
    private void grantPlan(PaymentOrder order) {
        SpaceStateVO space = imageSpaceClient.mine(order.getUserId()).getData();
        if (space == null) {
            throw new BusinessException(ErrorCode.PLAN_NOT_PURCHASABLE, "私有空间不存在，无法授予套餐");
        }
        LocalDateTime base = space.getTierExpireTime() == null ? LocalDateTime.now() : space.getTierExpireTime();
        imageSpaceClient.grant(new SpaceGrantPayload(order.getUserId(), order.getTier(), base.plusDays(PLAN_DAYS)));
    }

    private OrderVO withPayEntry(PaymentOrder order) {
        PayResult pay = paymentChannel.createPay(order);
        OrderVO vo = OrderVO.from(order, null);
        vo.setMockPay(pay.isMockPay());
        vo.setRedirectForm(pay.getRedirectForm());
        return vo;
    }

    /** 懒关单：读到已过期的 UNPAID 就当场置 CLOSED，避免列表显示与真实状态脱节 */
    private void refreshIfExpired(PaymentOrder order) {
        if (!PaymentOrder.STATUS_UNPAID.equals(order.getStatus())
                || order.getExpireTime() == null || order.getExpireTime().isAfter(LocalDateTime.now())) {
            return;
        }
        paymentOrderMapper.update(null, new LambdaUpdateWrapper<PaymentOrder>()
                .set(PaymentOrder::getStatus, PaymentOrder.STATUS_CLOSED)
                .eq(PaymentOrder::getOrderNo, order.getOrderNo())
                .eq(PaymentOrder::getStatus, PaymentOrder.STATUS_UNPAID));
        order.setStatus(PaymentOrder.STATUS_CLOSED);
    }

    private void requireUnpaid(PaymentOrder order, String message) {
        if (!PaymentOrder.STATUS_UNPAID.equals(order.getStatus())) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, message);
        }
    }

    private PaymentOrder requireMyOrder(String orderNo) {
        PaymentOrder order = requireOrder(orderNo);
        if (!order.getUserId().equals(CurrentUser.get().getId())) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
        }
        return order;
    }

    private PaymentOrder requireOrder(String orderNo) {
        PaymentOrder order = paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
        }
        return order;
    }

    private PageData<OrderVO> toPage(Page<PaymentOrder> page) {
        List<OrderVO> records = page.getRecords().stream().map(order -> OrderVO.from(order, null)).toList();
        return PageData.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 单号：PC + 时间戳 + 6 位随机；唯一性由 uk_order_no 兜底 */
    private static String nextOrderNo() {
        return "PC" + LocalDateTime.now().format(ORDER_NO_TIME)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}