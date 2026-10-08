import type { Id, OrderStatus, PageData, PaymentOrder, Tier } from './types'
import { get } from './http'

export interface AdminOrderQuery {
  current: number
  size: number
  userId?: Id
  status?: OrderStatus
  tier?: Tier
}

/** 管理端订单流水：只读，仅支持三个筛选条件 */
export function listAdminOrders(query: AdminOrderQuery): Promise<PageData<PaymentOrder>> {
  return get<PageData<PaymentOrder>>('/admin/payment/order/list', query)
}