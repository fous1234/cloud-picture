package com.example.cloudpicture.payment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.cloudpicture.common.api.ApiResponse;
import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import com.example.cloudpicture.common.security.context.CurrentUser;
import com.example.cloudpicture.common.tier.TierPlan;
import com.example.cloudpicture.payment.channel.PayResult;
import com.example.cloudpicture.payment.channel.PaymentChannel;
import com.example.cloudpicture.payment.client.ImageSpaceClient;
import com.example.cloudpicture.payment.config.PaymentProperties;
import com.example.cloudpicture.payment.dto.request.CreateOrderRequest;
import com.example.cloudpicture.payment.dto.request.SpaceGrantPayload;
import com.example.cloudpicture.payment.dto.response.OrderVO;
import com.example.cloudpicture.payment.dto.response.PlanListVO;
import com.example.cloudpicture.payment.dto.response.PlanVO;
import com.example.cloudpicture.payment.dto.response.SpaceStateVO;
import com.example.cloudpicture.payment.entity.PaymentOrder;
import com.example.cloudpicture.payment.mapper.PaymentOrderMapper;
import com.example.cloudpicture.payment.service.impl.PaymentOrderServiceImpl;
import java.time.LocalDateTime;
import java.util.Collection;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PaymentOrderServiceTest {

    private static final long USER_ID = 42L;
    private static final int PLAN_DAYS = 30;
    private static final String ORDER_NO = "PC20261007120345A7F2C1";
    private static final int GRANTED_FLAG = 1;
    private static final int NOT_GRANTED_FLAG = 0;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PaymentOrder.class);
        CurrentUser currentUser = new CurrentUser();
        currentUser.setId(USER_ID);
        currentUser.setRole(CurrentUser.ROLE_USER);
        CurrentUser.set(currentUser);
    }

    @AfterEach
    void tearDown() {
        CurrentUser.clear();
    }

    @Test
    void listPlansMarksAllUnpurchasableWhenNoSpace() {
        Fixture fixture = new Fixture();
        fixture.mineReturns(null);

        PlanListVO result = fixture.service.listPlans();

        assertFalse(result.isSpaceCreated());
        assertEquals(TierPlan.FREE.name(), result.getCurrentTier());
        assertNull(result.getExpireTime());
        result.getPlans().forEach(plan -> assertFalse(plan.isPurchasable()));
    }

    @Test
    void listPlansBlocksLowerTierButAllowsRenewForProUser() {
        Fixture fixture = new Fixture();
        LocalDateTime expireTime = LocalDateTime.now().plusDays(10);
        fixture.mineReturns(fixture.spaceState(TierPlan.PRO.name(), expireTime));

        PlanListVO result = fixture.service.listPlans();

        assertTrue(result.isSpaceCreated());
        assertEquals(TierPlan.PRO.name(), result.getCurrentTier());
        assertEquals(expireTime, result.getExpireTime());
        assertFalse(planOf(result, TierPlan.FREE).isPurchasable());
        // 同档保持可购买：前端把按钮渲染成"续费 30 天"
        assertTrue(planOf(result, TierPlan.PRO).isPurchasable());
        assertTrue(planOf(result, TierPlan.MAX).isPurchasable());
        assertTrue(planOf(result, TierPlan.PRO).isCurrent());
    }

    @Test
    void createOrderRejectsFreeTier() {
        Fixture fixture = new Fixture();

        BusinessException error = assertThrows(BusinessException.class,
                () -> fixture.service.createOrder(createRequest(TierPlan.FREE.name())));

        assertEquals(ErrorCode.PLAN_NOT_PURCHASABLE, error.getErrorCode());
        verify(fixture.mapper, never()).insert(any(PaymentOrder.class));
    }

    @Test
    void createOrderRejectsWhenSpaceNotCreated() {
        Fixture fixture = new Fixture();
        fixture.mineReturns(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> fixture.service.createOrder(createRequest(TierPlan.PRO.name())));

        assertEquals(ErrorCode.PLAN_NOT_PURCHASABLE, error.getErrorCode());
        verify(fixture.mapper, never()).insert(any(PaymentOrder.class));
    }

    @Test
    void createOrderRejectsDowngrade() {
        Fixture fixture = new Fixture();
        fixture.mineReturns(fixture.spaceState(TierPlan.MAX.name(), LocalDateTime.now().plusDays(10)));

        BusinessException error = assertThrows(BusinessException.class,
                () -> fixture.service.createOrder(createRequest(TierPlan.PRO.name())));

        assertEquals(ErrorCode.PLAN_NOT_PURCHASABLE, error.getErrorCode());
    }

    @Test
    void createOrderPricessTargetTierAndReturnsPayEntry() {
        Fixture fixture = new Fixture();
        fixture.mineReturns(fixture.spaceState(TierPlan.FREE.name(), null));
        PayResult payResult = PayResult.mock();
        when(fixture.channel.createPay(any(PaymentOrder.class))).thenReturn(payResult);

        OrderVO vo = fixture.service.createOrder(createRequest(TierPlan.MAX.name()));

        ArgumentCaptor<PaymentOrder> captor = ArgumentCaptor.forClass(PaymentOrder.class);
        verify(fixture.mapper).insert(captor.capture());
        PaymentOrder saved = captor.getValue();
        assertEquals(USER_ID, saved.getUserId());
        assertEquals(TierPlan.MAX.name(), saved.getTier());
        assertEquals(TierPlan.MAX.getPriceFen(), saved.getAmount());
        assertEquals(PaymentOrder.STATUS_UNPAID, saved.getStatus());
        assertEquals(PaymentOrder.CHANNEL_MOCK, saved.getChannel());
        assertTrue(saved.getOrderNo().startsWith("PC"));
        assertTrue(saved.getExpireTime().isAfter(LocalDateTime.now()));
        assertTrue(vo.isMockPay());
    }

    @Test
    void markPaidRejectsAmountMismatch() {
        Fixture fixture = new Fixture();
        fixture.selectOrder(unpaidOrder());

        BusinessException error = assertThrows(BusinessException.class,
                () -> fixture.service.markPaid(ORDER_NO, "trade-1", 1));

        assertEquals(ErrorCode.PAY_CHANNEL_ERROR, error.getErrorCode());
        verify(fixture.mapper, never()).update(isNull(), any(Wrapper.class));
        verify(fixture.imageSpaceClient, never()).grant(any(SpaceGrantPayload.class));
    }

    @Test
    void markPaidRejectsClosedOrder() {
        Fixture fixture = new Fixture();
        PaymentOrder order = unpaidOrder();
        order.setStatus(PaymentOrder.STATUS_CLOSED);
        fixture.selectOrder(order);

        BusinessException error = assertThrows(BusinessException.class,
                () -> fixture.service.markPaid(ORDER_NO, "trade-1", 990));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID, error.getErrorCode());
    }

    @Test
    void markPaidExtendsExpireTimeWhenRenewingBeforeExpiry() {
        Fixture fixture = new Fixture();
        fixture.selectOrder(unpaidOrder());
        when(fixture.mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        LocalDateTime currentExpire = LocalDateTime.now().plusDays(10);
        fixture.mineReturns(fixture.spaceState(TierPlan.PRO.name(), currentExpire));

        assertTrue(fixture.service.markPaid(ORDER_NO, "trade-1", 990));

        assertEquals(currentExpire.plusDays(PLAN_DAYS), capturedGrantPayload(fixture).getTierExpireTime());
    }

    @Test
    void markPaidStartsFromNowWhenSpaceIsFree() {
        Fixture fixture = new Fixture();
        fixture.selectOrder(unpaidOrder());
        fixture.mineReturns(fixture.spaceState(TierPlan.FREE.name(), null));

        assertTrue(fixture.service.markPaid(ORDER_NO, "trade-1", 990));

        SpaceGrantPayload granted = capturedGrantPayload(fixture);
        LocalDateTime expireTime = granted.getTierExpireTime();
        assertTrue(expireTime.isAfter(LocalDateTime.now().plusDays(PLAN_DAYS - 1)));
        assertEquals(TierPlan.PRO.name(), granted.getTier());
    }

    @Test
    void markPaidRetriesGrantWhenFlagStillCleared() {
        Fixture fixture = new Fixture();
        PaymentOrder paid = unpaidOrder();
        paid.setStatus(PaymentOrder.STATUS_PAID);
        fixture.selectOrder(paid);
        fixture.mineReturns(fixture.spaceState(TierPlan.PRO.name(), null));

        assertTrue(fixture.service.markPaid(ORDER_NO, "trade-1", 990));

        // 已 PAID 不再流转状态，但仍要尝试授权：上一次授权失败时靠这条路径补上
        verify(fixture.imageSpaceClient).grant(any(SpaceGrantPayload.class));
    }

    @Test
    void markPaidGrantsAtMostOnceAcrossRepeatedCalls() {
        Fixture fixture = new Fixture();
        fixture.selectOrder(unpaidOrder());
        fixture.mineReturns(fixture.spaceState(TierPlan.PRO.name(), null));

        assertTrue(fixture.service.markPaid(ORDER_NO, "trade-1", 990));
        verify(fixture.imageSpaceClient).grant(any(SpaceGrantPayload.class));

        // 第二次调用抢不到 granted 标记 → 一根头发都不能再送（防"重复点模拟支付刷会员"）
        when(fixture.mapper.update(isNull(), any(Wrapper.class))).thenReturn(0);
        assertTrue(fixture.service.markPaid(ORDER_NO, "trade-1", 990));

        verify(fixture.imageSpaceClient, times(1)).grant(any(SpaceGrantPayload.class));
    }

    @Test
    void markPaidReleasesGrantFlagSoChannelRetryCanRegrant() {
        Fixture fixture = new Fixture();
        fixture.selectOrder(unpaidOrder());
        fixture.mineReturns(fixture.spaceState(TierPlan.PRO.name(), null));
        RuntimeException channelDown = new RuntimeException("image-service 不可用");
        when(fixture.imageSpaceClient.grant(any(SpaceGrantPayload.class))).thenThrow(channelDown);

        assertThrows(RuntimeException.class, () -> fixture.service.markPaid(ORDER_NO, "trade-1", 990));

        // 先抢标记写 1、失败后必须退回 0，否则渠道重试永远补不上授权
        assertTrue(capturedUpdateValues(fixture).contains(GRANTED_FLAG));
        assertTrue(capturedUpdateValues(fixture).contains(NOT_GRANTED_FLAG));
    }

    @Test
    void cancelRejectsPaidOrder() {
        Fixture fixture = new Fixture();
        PaymentOrder paid = unpaidOrder();
        paid.setStatus(PaymentOrder.STATUS_PAID);
        fixture.selectOrder(paid);

        BusinessException error = assertThrows(BusinessException.class,
                () -> fixture.service.cancel(ORDER_NO));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID, error.getErrorCode());
    }

    @Test
    void cancelClosesExpiredUnpaidOrderInsteadOfCancelling() {
        Fixture fixture = new Fixture();
        PaymentOrder order = unpaidOrder();
        order.setExpireTime(LocalDateTime.now().minusMinutes(1));
        fixture.selectOrder(order);

        BusinessException error = assertThrows(BusinessException.class,
                () -> fixture.service.cancel(ORDER_NO));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID, error.getErrorCode());
        // 懒关单把订单置为 CLOSED，而不是 CANCELED
        assertTrue(capturedUpdateValues(fixture).contains(PaymentOrder.STATUS_CLOSED));
        assertFalse(capturedUpdateValues(fixture).contains(PaymentOrder.STATUS_CANCELED));
    }

    @Test
    void getOrderHidesOtherUsersOrder() {
        Fixture fixture = new Fixture();
        PaymentOrder foreign = unpaidOrder();
        foreign.setUserId(999L);
        fixture.selectOrder(foreign);

        BusinessException error = assertThrows(BusinessException.class,
                () -> fixture.service.getOrder(ORDER_NO));

        assertEquals(ErrorCode.ORDER_NOT_FOUND, error.getErrorCode());
    }

    @Test
    void mockPayRejectsAlipayChannelOrder() {
        Fixture fixture = new Fixture();
        PaymentOrder order = unpaidOrder();
        order.setChannel(PaymentOrder.CHANNEL_ALIPAY);
        fixture.selectOrder(order);

        BusinessException error = assertThrows(BusinessException.class,
                () -> fixture.service.mockPay(ORDER_NO));

        assertEquals(ErrorCode.ORDER_STATUS_INVALID, error.getErrorCode());
    }

    private static SpaceGrantPayload capturedGrantPayload(Fixture fixture) {
        ArgumentCaptor<SpaceGrantPayload> captor = ArgumentCaptor.forClass(SpaceGrantPayload.class);
        verify(fixture.imageSpaceClient).grant(captor.capture());
        return captor.getValue();
    }

    /** 汇总所有 update 调用里绑定过的参数值，用来判断一共写了哪些值 */
    @SuppressWarnings("unchecked")
    private static Collection<Object> capturedUpdateValues(Fixture fixture) {
        ArgumentCaptor<Wrapper<PaymentOrder>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(fixture.mapper, atLeastOnce()).update(isNull(), captor.capture());
        return captor.getAllValues().stream()
                .flatMap(wrapper -> ((LambdaUpdateWrapper<PaymentOrder>) wrapper)
                        .getParamNameValuePairs().values().stream())
                .toList();
    }

    private static PlanVO planOf(PlanListVO result, TierPlan tier) {
        return result.getPlans().stream().filter(plan -> tier.name().equals(plan.getTier())).findFirst().orElseThrow();
    }

    private static CreateOrderRequest createRequest(String tier) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setTier(tier);
        return request;
    }

    private static PaymentOrder unpaidOrder() {
        PaymentOrder order = new PaymentOrder();
        order.setId(1L);
        order.setOrderNo(ORDER_NO);
        order.setUserId(USER_ID);
        order.setTier(TierPlan.PRO.name());
        order.setAmount(TierPlan.PRO.getPriceFen());
        order.setStatus(PaymentOrder.STATUS_UNPAID);
        order.setChannel(PaymentOrder.CHANNEL_MOCK);
        order.setExpireTime(LocalDateTime.now().plusMinutes(30));
        return order;
    }

    /** 统一装配被测服务与各 mock，避免每个用例重复构造函数 */
    private static final class Fixture {

        private final PaymentOrderMapper mapper = mock(PaymentOrderMapper.class);
        private final ImageSpaceClient imageSpaceClient = mock(ImageSpaceClient.class);
        private final PaymentChannel channel = mock(PaymentChannel.class);
        private final PaymentOrderServiceImpl service = new PaymentOrderServiceImpl(
                mapper, imageSpaceClient, channel, new PaymentProperties());

        Fixture() {
            when(channel.name()).thenReturn(PaymentOrder.CHANNEL_MOCK);
        }

        void mineReturns(SpaceStateVO state) {
            when(imageSpaceClient.mine(anyLong())).thenReturn(ApiResponse.success(state));
        }

        void selectOrder(PaymentOrder order) {
            when(mapper.selectOne(any(Wrapper.class))).thenReturn(order);
            when(imageSpaceClient.grant(any(SpaceGrantPayload.class))).thenReturn(ApiResponse.success(true));
            // 默认放行授权（具体返回值由用例按需覆盖）
            when(mapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        }

        SpaceStateVO spaceState(String tier, LocalDateTime expireTime) {
            SpaceStateVO state = new SpaceStateVO();
            state.setSpaceId(7L);
            state.setTier(tier);
            state.setTierExpireTime(expireTime);
            return state;
        }
    }
}