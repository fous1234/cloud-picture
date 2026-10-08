import { computed, ref } from 'vue'
import type { OrderChannel, OrderStatus, PaymentOrder, SpacePlan, Tier } from '../api/types'
import { getOrder } from '../api/payment'

/** 订阅周期天数：与后端 PLAN_DAYS 及 /plans 的 planDays 一致。
 *  订单接口不返回该字段，故订单相关页面集中取这一份；改周期时两处一起改。 */
export const PLAN_DAYS = 30

/** 金额（分）→ ¥9.9（不补零） */
export function formatPrice(fen: number): string {
  return `¥${(fen / 100).toFixed(2).replace(/\.00$/, '')}`
}

/** 字节 → 整数 MB，用于限额展示（200 MB / 350 MB / 600 MB） */
export function mbText(bytes: number): string {
  return `${Math.round(bytes / (1024 * 1024))} MB`
}

interface QuotaView {
  imageCount: number
  totalSize: number
  imageLimit: number
  sizeLimitBytes: number
}

/** 已用 / 上限的百分比；上限为 0 或缺失时按 0 处理，超过 100% 截断 */
export function quotaPercent(used: number, limit: number): number {
  if (!limit || limit <= 0) return 0
  return Math.min(100, (used / limit) * 100)
}

/** 单一维度是否已到上限 */
export function quotaReached(used: number, limit: number): boolean {
  return limit > 0 && used >= limit
}

/** 张数与容量任一到达上限即为满（先到先限） */
export function quotaFull(space: QuotaView): boolean {
  return (
    quotaReached(space.imageCount, space.imageLimit) ||
    quotaReached(space.totalSize, space.sizeLimitBytes)
  )
}

/** 超限提示文案（标题区上传按钮 tooltip 用） */
export function quotaAlertText(space: QuotaView): string {
  return `当前套餐已满（${space.imageLimit} 张 / ${mbText(space.sizeLimitBytes)}），去升级`
}

/** 额度卡上的超限告警文案 */
export const QUOTA_FULL_TEXT = '空间已满，升级解锁'

export function isFreePlan(plan: SpacePlan): boolean {
  return plan.tier === 'FREE'
}

export function planPriceText(plan: SpacePlan): string {
  return isFreePlan(plan) ? '免费' : formatPrice(plan.priceFen)
}

/** FREE 显示「永久有效」：后端对 FREE 也返回 planDays = 30 */
export function planPeriodText(plan: SpacePlan): string {
  return isFreePlan(plan) ? '/ 永久有效' : `/ ${plan.planDays} 天`
}

const PLAN_DESC: Record<Tier, string> = {
  FREE: '入门探索，满足个人日常图库需求',
  PRO: '进阶使用，适合持续上传与整理影像',
  MAX: '重度使用，容纳更多原图与长期沉淀',
}

export function planDesc(plan: SpacePlan): string {
  return PLAN_DESC[plan.tier]
}

export function planBenefits(plan: SpacePlan): string[] {
  const benefits = [`存储容量 ${mbText(plan.sizeLimitBytes)}`, `图片上限 ${plan.imageLimit} 张`]
  if (plan.tier === 'FREE') {
    benefits.push('免审核直传', '仅自己可见')
  } else if (plan.tier === 'PRO') {
    benefits.push('含普通档全部权益', '到期自动降回普通')
  } else {
    benefits.push('含 PRO 档全部权益', '到期自动降回普通')
  }
  return benefits
}

export interface PlanAction {
  text: string
  /** primary = 可点击主按钮；disabled = 当前档或不可购买 */
  kind: 'primary' | 'disabled'
}

/** 卡片按钮三态：可购买性一律以后端 purchasable / current 为准，前端不抢答 */
export function planAction(plan: SpacePlan, spaceCreated: boolean): PlanAction {
  if (!spaceCreated) return { text: '请先创建私有空间', kind: 'disabled' }
  if (plan.current) {
    return plan.purchasable
      ? { text: `续费 ${plan.planDays} 天`, kind: 'primary' }
      : { text: '当前套餐', kind: 'disabled' }
  }
  return plan.purchasable
    ? { text: '升级套餐', kind: 'primary' }
    : { text: '已在更高档套餐｜无法购买', kind: 'disabled' }
}

/** 订单状态色调，组件据此映射到既有 --cp-status-* 令牌（不新增颜色） */
export type OrderStatusTone = 'pending' | 'approved' | 'muted'

const ORDER_STATUS: Record<OrderStatus, { text: string; tone: OrderStatusTone }> = {
  UNPAID: { text: '待支付', tone: 'pending' },
  PAID: { text: '已支付', tone: 'approved' },
  CANCELED: { text: '已取消', tone: 'muted' },
  CLOSED: { text: '已关闭', tone: 'muted' },
}

export function orderStatusText(status: OrderStatus): string {
  return ORDER_STATUS[status]?.text ?? status
}

export function orderStatusTone(status: OrderStatus): OrderStatusTone {
  return ORDER_STATUS[status]?.tone ?? 'muted'
}

/** 状态筛选项；「全部状态」由 Select 的 allow-clear 承担 */
export const ORDER_STATUS_OPTIONS: { label: string; value: OrderStatus }[] = (
  ['UNPAID', 'PAID', 'CANCELED', 'CLOSED'] as OrderStatus[]
).map((status) => ({ label: orderStatusText(status), value: status }))

/** 档位筛选项：订单只会出现 PRO / MAX */
export const TIER_FILTER_OPTIONS: { label: string; value: Tier }[] = [
  { label: 'PRO', value: 'PRO' },
  { label: 'MAX', value: 'MAX' },
]

const CHANNEL_TEXT: Record<OrderChannel, string> = { MOCK: '模拟', ALIPAY: '支付宝' }

/** 列表里的简写渠道名 */
export function channelText(channel: OrderChannel): string {
  return CHANNEL_TEXT[channel] ?? channel
}

/** 「支付方式」文案：只展示不可选 */
export function channelPayText(channel: OrderChannel): string {
  return channel === 'MOCK' ? '模拟支付' : '支付宝（沙箱）'
}

/** alipay 通道：把后端返回的收银台表单写进新窗口并提交；被浏览器拦截时返回 false */
export function submitRedirectForm(html: string): boolean {
  const win = window.open('', '_blank')
  if (!win) return false
  win.document.write(html)
  win.document.close()
  return true
}

export const ORDER_POLL_INTERVAL_MS = 2000
export const ORDER_POLL_TIMEOUT_MS = 5 * 60 * 1000

/**
 * 待支付倒计时（mm:ss）：支付弹窗与订单详情页共用。
 * start 传入订单到期时间，归零时可选回调；stop 清理定时器（onUnmounted 必须调用）。
 */
export function createCountdown() {
  const remainingMs = ref(0)
  let timer: number | undefined

  const remainingText = computed(() => {
    const total = Math.max(0, Math.floor(remainingMs.value / 1000))
    return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`
  })

  function stop() {
    if (timer !== undefined) window.clearInterval(timer)
    timer = undefined
  }

  function sync(expireTime: string | null, onExpired?: () => void) {
    if (!expireTime) {
      remainingMs.value = 0
      return
    }
    const deadline = new Date(expireTime.replace(' ', 'T')).getTime()
    remainingMs.value = Math.max(0, deadline - Date.now())
    if (remainingMs.value === 0) {
      stop()
      onExpired?.()
    }
  }

  function start(expireTime: string | null, onExpired?: () => void) {
    stop()
    sync(expireTime, onExpired)
    timer = window.setInterval(() => sync(expireTime, onExpired), 1000)
  }

  return { remainingMs, remainingText, start, stop }
}

/**
 * 轮询订单直到 PAID / CLOSED 或超时，每次拿到订单都回调 onUpdate。
 * 返回 stop()，页面 onUnmounted 时必须调用以清理定时器。
 */
export function pollOrderUntilSettled(
  orderNo: string,
  onUpdate: (order: PaymentOrder) => void,
  onError?: (error: unknown) => void,
): () => void {
  let timer: number | undefined
  let stopped = false
  const deadline = Date.now() + ORDER_POLL_TIMEOUT_MS

  const stop = () => {
    stopped = true
    if (timer !== undefined) window.clearTimeout(timer)
    timer = undefined
  }

  const tick = async () => {
    if (stopped) return
    try {
      const order = await getOrder(orderNo)
      if (stopped) return
      onUpdate(order)
      if (order.status === 'PAID' || order.status === 'CLOSED') {
        stop()
        return
      }
    } catch (error) {
      if (stopped) return
      stop()
      onError?.(error)
      return
    }
    if (Date.now() >= deadline) {
      stop()
      return
    }
    timer = window.setTimeout(tick, ORDER_POLL_INTERVAL_MS)
  }

  timer = window.setTimeout(tick, ORDER_POLL_INTERVAL_MS)
  return stop
}