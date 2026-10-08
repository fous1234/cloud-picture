import type { OrderQuery, PageData, PaymentOrder, PlanList, Tier } from './types'
import { get, post } from './http'

/** 套餐目录 + 当前档位：驱动套餐卡片的三态按钮与徽标 */
export function getPlans(): Promise<PlanList> {
  return get<PlanList>('/payment/plans')
}

/** 下单：返回订单与支付入口（mockPay 标记或支付宝 redirectForm） */
export function createOrder(tier: Tier): Promise<PaymentOrder> {
  return post<PaymentOrder>('/payment/order', { tier })
}

/** 继续支付：复用同一订单，重新取支付入口 */
export function payOrder(orderNo: string): Promise<PaymentOrder> {
  return post<PaymentOrder>(`/payment/order/${orderNo}/pay`)
}

/** 单笔查询：订单详情与支付后的状态轮询都用它 */
export function getOrder(orderNo: string): Promise<PaymentOrder> {
  return get<PaymentOrder>(`/payment/order/${orderNo}`)
}

/** 我的订单：只能查到自己的订单 */
export function listMyOrders(query: OrderQuery): Promise<PageData<PaymentOrder>> {
  return get<PageData<PaymentOrder>>('/payment/orders', query)
}

export function cancelOrder(orderNo: string): Promise<boolean> {
  return post<boolean>(`/payment/order/${orderNo}/cancel`)
}

/** 内置模拟支付：仅 MOCK 通道订单可用；成功后仍以轮询到的订单状态为准 */
export function mockPay(orderNo: string): Promise<boolean> {
  return post<boolean>(`/payment/order/${orderNo}/mock-pay`)
}