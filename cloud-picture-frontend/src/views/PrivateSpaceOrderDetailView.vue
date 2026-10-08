<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import {
  Button,
  Descriptions,
  DescriptionsItem,
  Modal,
  Skeleton,
  message,
} from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import type { RouteLocationRaw } from 'vue-router'
import type { PaymentOrder } from '../api/types'
import { cancelOrder, getOrder, payOrder } from '../api/payment'
import { ApiError, errorMessage } from '../api/http'
import { formatDateTime } from '../utils/format'
import {
  PLAN_DAYS,
  channelPayText,
  createCountdown,
  formatPrice,
  orderStatusText,
  orderStatusTone,
  pollOrderUntilSettled,
  submitRedirectForm,
} from '../utils/payment'
import AppBreadcrumb from '../components/AppBreadcrumb.vue'
import EmptyState from '../components/EmptyState.vue'
import ErrorState from '../components/ErrorState.vue'
import OrderPayModal from '../components/OrderPayModal.vue'

const route = useRoute()
const router = useRouter()

const crumbs: { label: string; to?: RouteLocationRaw; back?: boolean }[] = [
  { label: '首页', to: { name: 'gallery' } },
  { label: '私有空间', to: { name: 'private-space' } },
  { label: '我的订单', back: true },
  { label: '订单详情' },
]

const order = ref<PaymentOrder | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const notFound = ref(false)

/** 支付步骤（MOCK）；alipay 直接跳收银台 */
const payOpen = ref(false)
const payEntry = ref<PaymentOrder | null>(null)
const payingOut = ref(false)

const { remainingText, start: startCountdown, stop: stopCountdown } = createCountdown()
let stopPoll: (() => void) | null = null

const orderNo = computed(() => String(route.params.orderNo ?? ''))

const statusHint = computed(() => {
  const current = order.value
  if (!current) return ''
  switch (current.status) {
    case 'UNPAID':
      return `请在 ${remainingText.value} 前完成支付，超时将自动关闭`
    case 'PAID':
      return `已支付，套餐将于 ${formatDateTime(current.tierExpireTime)} 到期`
    case 'CANCELED':
      return '订单已取消'
    default:
      return '订单已超时关闭'
  }
})

async function load() {
  loading.value = true
  error.value = null
  notFound.value = false
  try {
    order.value = await getOrder(orderNo.value)
    afterLoad()
  } catch (e) {
    order.value = null
    if (e instanceof ApiError && e.code === 'ORDER_NOT_FOUND') notFound.value = true
    else error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

/** 待支付则启动倒计时与轮询；PAID / CLOSED 都算终态 */
function afterLoad() {
  stopPoll?.()
  stopPoll = null
  stopCountdown()
  const current = order.value
  if (!current || current.status !== 'UNPAID') return
  startCountdown(current.expireTime)
  stopPoll = pollOrderUntilSettled(current.orderNo, (fresh) => {
    order.value = fresh
    if (fresh.status !== 'UNPAID') stopCountdown()
  })
}

function goOrders() {
  router.push({ name: 'private-space-orders' })
}

async function confirmPay() {
  const current = order.value
  if (!current || payingOut.value) return
  payingOut.value = true
  try {
    const entry = await payOrder(current.orderNo)
    if (entry.mockPay) {
      payEntry.value = entry
      payOpen.value = true
      return
    }
    const opened = entry.redirectForm ? submitRedirectForm(entry.redirectForm) : false
    if (!opened) message.warning('支付窗口被浏览器拦截，请重试')
    // 当前页留在详情，靠轮询接住支付结果
  } catch (e) {
    message.error(errorMessage(e))
    load()
  } finally {
    payingOut.value = false
  }
}

function onPaid(settled: PaymentOrder) {
  payEntry.value = null
  order.value = settled
  stopPoll?.()
  stopPoll = null
  stopCountdown()
  message.success('支付成功，套餐已生效')
}

function confirmCancel() {
  const current = order.value
  if (!current) return
  Modal.confirm({
    title: '确认取消这笔订单？',
    content: `订单 ${current.orderNo} 将变为已取消，之后无法再支付。`,
    okText: '取消订单',
    okType: 'danger',
    cancelText: '再想想',
    async onOk() {
      try {
        await cancelOrder(current.orderNo)
        message.success('订单已取消')
        await load()
      } catch (e) {
        message.error(errorMessage(e))
        throw e
      }
    },
  })
}

watch(
  () => route.params.orderNo,
  () => load(),
  { immediate: true },
)

onUnmounted(() => {
  stopPoll?.()
  stopCountdown()
})
</script>

<template>
  <div class="cp-container cp-page order-detail-page">
    <AppBreadcrumb :items="crumbs" />

    <Skeleton v-if="loading" :paragraph="{ rows: 8 }" active />

    <ErrorState
      v-else-if="error"
      message="订单加载失败"
      :description="error"
      @retry="load"
    />

    <EmptyState v-else-if="notFound" description="订单不存在，或不属于当前账号">
      <Button type="primary" @click="goOrders">返回订单列表</Button>
    </EmptyState>

    <template v-else-if="order">
      <div class="detail-head">
        <h1 class="cp-page-title">订单详情</h1>
        <p class="detail-order-no">{{ order.orderNo }}</p>
      </div>

      <!-- 状态区 -->
      <section class="detail-status">
        <span class="order-status" :class="`is-${orderStatusTone(order.status)}`">
          {{ orderStatusText(order.status) }}
        </span>
        <p class="detail-status-hint">{{ statusHint }}</p>
      </section>

      <!-- 明细区 -->
      <section class="detail-panel">
        <Descriptions :column="2" size="middle">
          <DescriptionsItem label="套餐">{{ order.tierName }}</DescriptionsItem>
          <DescriptionsItem label="金额">{{ formatPrice(order.amountFen) }}</DescriptionsItem>
          <DescriptionsItem label="有效期">{{ PLAN_DAYS }} 天</DescriptionsItem>
          <DescriptionsItem label="支付方式">{{ channelPayText(order.channel) }}</DescriptionsItem>
          <DescriptionsItem label="订单状态">{{ orderStatusText(order.status) }}</DescriptionsItem>
          <DescriptionsItem label="创建时间">{{ formatDateTime(order.createTime) }}</DescriptionsItem>
          <DescriptionsItem label="支付超时时间">{{ formatDateTime(order.expireTime) }}</DescriptionsItem>
          <DescriptionsItem label="支付时间">{{ formatDateTime(order.payTime) }}</DescriptionsItem>
          <DescriptionsItem label="交易号">
            <span class="detail-mono">{{ order.transactionId ?? '—' }}</span>
          </DescriptionsItem>
        </Descriptions>
      </section>

      <!-- 生效结果区（仅已支付） -->
      <section v-if="order.status === 'PAID'" class="detail-active">
        已生效：档位 {{ order.tierName }}，有效期至 {{ formatDateTime(order.tierExpireTime) }}
      </section>

      <!-- 操作区 -->
      <section class="detail-actions">
        <template v-if="order.status === 'UNPAID'">
          <Button type="primary" :loading="payingOut" @click="confirmPay">确认支付</Button>
          <Button danger @click="confirmCancel">取消订单</Button>
        </template>
        <Button v-else @click="goOrders">返回订单列表</Button>
      </section>
    </template>

    <OrderPayModal v-model:open="payOpen" :order="payEntry" @paid="onPaid" />
  </div>
</template>

<style scoped>
.order-detail-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-head {
  margin-top: 4px;
}

.detail-order-no {
  margin: 6px 0 0;
  color: var(--cp-text-soft);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
}

.detail-status {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px 24px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-surface);
  box-shadow: var(--cp-shadow-subtle);
}

.detail-status-hint {
  margin: 0;
  color: var(--cp-text-soft);
  font-size: 13px;
}

.detail-panel {
  padding: 20px 24px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-surface);
  box-shadow: var(--cp-shadow-subtle);
}

.detail-active {
  padding: 14px 20px;
  border: 1px solid var(--cp-status-approved-border);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-status-approved-bg);
  color: var(--cp-status-approved-fg);
  font-size: 13px;
  font-weight: 600;
}

.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.detail-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.order-status {
  display: inline-flex;
  align-items: center;
  padding: 3px 12px;
  border: 1px solid transparent;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}

.order-status.is-pending {
  border-color: var(--cp-status-pending-border);
  background: var(--cp-status-pending-bg);
  color: var(--cp-status-pending-fg);
}

.order-status.is-approved {
  border-color: var(--cp-status-approved-border);
  background: var(--cp-status-approved-bg);
  color: var(--cp-status-approved-fg);
}

.order-status.is-muted {
  border-color: var(--cp-border);
  background: var(--cp-bg-soft);
  color: var(--cp-text-muted);
}
</style>