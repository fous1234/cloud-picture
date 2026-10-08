<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { Button, Modal, Pagination, Select, Skeleton, Table, message } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import type { RouteLocationRaw } from 'vue-router'
import type { OrderStatus, PaymentOrder } from '../api/types'
import { cancelOrder, listMyOrders, payOrder } from '../api/payment'
import { errorMessage } from '../api/http'
import { formatDateTime, formatRelativeTime } from '../utils/format'
import {
  ORDER_STATUS_OPTIONS,
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

const DEFAULT_PAGE_SIZE = 10

const route = useRoute()
const router = useRouter()

const crumbs: { label: string; to?: RouteLocationRaw; back?: boolean }[] = [
  { label: '首页', to: { name: 'gallery' } },
  { label: '私有空间', back: true },
  { label: '我的订单' },
]

const orders = ref<PaymentOrder[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref<string | null>(null)
const statusInput = ref<OrderStatus | undefined>(undefined)

/** 支付步骤（MOCK）；alipay 直接跳收银台 */
const payOpen = ref(false)
const payEntry = ref<PaymentOrder | null>(null)
const payingOut = ref(false)

let requestSeq = 0
let stopPoll: (() => void) | null = null

function readQuery() {
  const asString = (value: unknown) => (typeof value === 'string' && value ? value : undefined)
  const current = Number(route.query.current) > 0 ? Number(route.query.current) : 1
  const size = Number(route.query.size) > 0 ? Number(route.query.size) : DEFAULT_PAGE_SIZE
  return {
    current,
    size,
    status: asString(route.query.status) as OrderStatus | undefined,
    orderNo: asString(route.query.orderNo),
  }
}

const query = computed(() => readQuery())
const highlightOrderNo = computed(() => query.value.orderNo)

async function load() {
  const { current, size, status } = readQuery()
  const seq = ++requestSeq
  loading.value = true
  error.value = null
  try {
    const page = await listMyOrders({ current, size, status })
    if (seq !== requestSeq) return
    orders.value = page.records ?? []
    total.value = page.total ?? 0
  } catch (e) {
    if (seq !== requestSeq) return
    orders.value = []
    total.value = 0
    error.value = errorMessage(e)
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

function pushQuery(patch: Record<string, string | number | undefined>) {
  const merged = { ...readQuery(), ...patch }
  const next: Record<string, string | number> = {}
  if (merged.status) next.status = merged.status
  if (merged.orderNo) next.orderNo = merged.orderNo
  if (merged.current > 1) next.current = merged.current
  if (merged.size !== DEFAULT_PAGE_SIZE) next.size = merged.size
  router.push({ name: 'private-space-orders', query: next })
}

function submitSearch() {
  pushQuery({ status: statusInput.value, current: 1 })
}

function changePage(page: number, size: number) {
  pushQuery({ current: page, size })
}

function goPlans() {
  router.push({ name: 'private-space-plans' })
}

function goDetail(orderNo: string) {
  router.push({ name: 'private-space-order-detail', params: { orderNo } })
}

/** 落到本页时若带 orderNo，就把该行高亮并轮询到支付终态 */
function watchOrder(orderNo: string | undefined) {
  stopPoll?.()
  stopPoll = null
  if (!orderNo) return
  stopPoll = pollOrderUntilSettled(orderNo, (order) => {
    const index = orders.value.findIndex((item) => item.orderNo === order.orderNo)
    if (index >= 0) orders.value[index] = order
    if (order.status === 'PAID') message.success('支付成功，套餐已生效')
  })
}

async function continuePay(record: PaymentOrder) {
  if (payingOut.value) return
  payingOut.value = true
  try {
    const entry = await payOrder(record.orderNo)
    if (entry.mockPay) {
      payEntry.value = entry
      payOpen.value = true
      return
    }
    const opened = entry.redirectForm ? submitRedirectForm(entry.redirectForm) : false
    if (!opened) message.warning('支付窗口被浏览器拦截，请重试')
    pushQuery({ orderNo: entry.orderNo })
  } catch (e) {
    message.error(errorMessage(e))
    load()
  } finally {
    payingOut.value = false
  }
}

function onPaid() {
  payEntry.value = null
  message.success('支付成功，套餐已生效')
  load()
}

function confirmCancel(record: PaymentOrder) {
  Modal.confirm({
    title: '确认取消这笔订单？',
    content: `订单 ${record.orderNo} 将变为已取消，之后无法再支付。`,
    okText: '取消订单',
    okType: 'danger',
    cancelText: '再想想',
    async onOk() {
      try {
        await cancelOrder(record.orderNo)
        message.success('订单已取消')
        load()
      } catch (e) {
        message.error(errorMessage(e))
        throw e
      }
    },
  })
}

const columns: TableColumnsType = [
  { title: '订单编号', key: 'orderNo', width: 210 },
  { title: '套餐', key: 'tier', width: 80 },
  { title: '金额', key: 'amount', width: 90 },
  { title: '状态', key: 'status', width: 100 },
  { title: '支付时间', key: 'payTime', width: 160 },
  { title: '创建时间', key: 'createTime', width: 120 },
  { title: '操作', key: 'action', width: 250 },
]

function rowClassName(record: PaymentOrder): string {
  return record.orderNo === highlightOrderNo.value ? 'order-row-highlight' : ''
}

// 窄屏用纵向卡片替代表格，避免横向滚动
const narrow = ref(false)
const mediaQuery = window.matchMedia('(max-width: 767px)')
function syncNarrow(event: MediaQueryList | MediaQueryListEvent) {
  narrow.value = event.matches
}
syncNarrow(mediaQuery)
onMounted(() => mediaQuery.addEventListener('change', syncNarrow))
onUnmounted(() => {
  mediaQuery.removeEventListener('change', syncNarrow)
  stopPoll?.()
})

watch(
  () => route.query,
  async () => {
    statusInput.value = readQuery().status
    // 先载入列表再起轮询，这样被轮询的订单行已经存在，可以直接原地更新
    await load()
    watchOrder(readQuery().orderNo)
  },
  { immediate: true },
)
</script>

<template>
  <div class="cp-container cp-page orders-page">
    <AppBreadcrumb :items="crumbs" />

    <div class="orders-head">
      <p class="section-kicker">ORDER HISTORY</p>
      <h1 class="cp-page-title">我的订单</h1>
      <p class="cp-page-subtitle">未支付订单可继续支付，30 分钟内有效</p>
    </div>

    <div class="cp-toolbar orders-toolbar">
      <Select
        v-model:value="statusInput"
        :options="ORDER_STATUS_OPTIONS"
        placeholder="全部状态"
        allow-clear
        class="orders-status"
      />
      <Button type="primary" @click="submitSearch">查询</Button>
      <span class="toolbar-count">共 {{ total }} 笔订单</span>
    </div>

    <Skeleton v-if="loading" :paragraph="{ rows: 6 }" active />

    <ErrorState v-else-if="error" message="订单列表加载失败" :description="error" @retry="load" />

    <EmptyState v-else-if="!orders.length" description="还没有订单记录">
      <Button type="primary" @click="goPlans">去升级套餐</Button>
    </EmptyState>

    <div v-else-if="!narrow" class="orders-table-panel">
      <Table
        :columns="columns"
        :data-source="orders"
        :pagination="false"
        :row-key="(record: PaymentOrder) => record.orderNo"
        :row-class-name="rowClassName"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'orderNo'">
            <span class="order-mono">{{ record.orderNo }}</span>
          </template>
          <template v-else-if="column.key === 'tier'">
            {{ record.tierName }}
          </template>
          <template v-else-if="column.key === 'amount'">
            {{ formatPrice(record.amountFen) }}
          </template>
          <template v-else-if="column.key === 'status'">
            <span class="order-status" :class="`is-${orderStatusTone(record.status)}`">
              {{ orderStatusText(record.status) }}
            </span>
          </template>
          <template v-else-if="column.key === 'payTime'">
            {{ formatDateTime(record.payTime) }}
          </template>
          <template v-else-if="column.key === 'createTime'">
            {{ formatRelativeTime(record.createTime) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <template v-if="record.status === 'UNPAID'">
              <Button size="small" type="primary" :loading="payingOut" @click="continuePay(record as PaymentOrder)">
                继续支付
              </Button>
              <Button size="small" danger @click="confirmCancel(record as PaymentOrder)">取消订单</Button>
            </template>
            <Button size="small" @click="goDetail((record as PaymentOrder).orderNo)">查看详情</Button>
          </template>
        </template>
      </Table>
    </div>

    <div v-else class="order-cards">
      <article v-for="order in orders" :key="order.orderNo" class="order-card">
        <div class="order-card-head">
          <span class="order-mono">{{ order.orderNo }}</span>
          <span class="order-status" :class="`is-${orderStatusTone(order.status)}`">
            {{ orderStatusText(order.status) }}
          </span>
        </div>
        <div class="order-card-meta">
          {{ order.tierName }} · {{ formatPrice(order.amountFen) }} · 创建于
          {{ formatRelativeTime(order.createTime) }}
        </div>
        <div class="order-card-meta">支付时间：{{ formatDateTime(order.payTime) }}</div>
        <div class="order-card-actions">
          <template v-if="order.status === 'UNPAID'">
            <Button block type="primary" :loading="payingOut" @click="continuePay(order)">继续支付</Button>
            <Button block danger @click="confirmCancel(order)">取消订单</Button>
          </template>
          <Button block @click="goDetail(order.orderNo)">查看详情</Button>
        </div>
      </article>
    </div>

    <div v-if="total > 0" class="cp-pagination">
      <Pagination
        :current="query.current"
        :page-size="query.size"
        :total="total"
        show-less-items
        @change="changePage"
      />
    </div>

    <OrderPayModal v-model:open="payOpen" :order="payEntry" @paid="onPaid" />
  </div>
</template>

<style scoped>
.orders-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.orders-head {
  margin-top: 4px;
}

.orders-toolbar {
  margin-top: 0;
  padding: 12px;
  border: 1px solid var(--cp-border-subtle);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-surface);
}

.orders-status {
  width: 160px;
}

.toolbar-count {
  margin-left: auto;
  color: var(--cp-text-soft);
  font-size: 13px;
}

.orders-table-panel {
  overflow: hidden;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius-lg);
  background: #fff;
  box-shadow: 0 8px 24px rgba(17, 24, 39, 0.04);
}

.orders-table-panel :deep(.ant-table) {
  background: transparent;
}

.orders-table-panel :deep(.ant-table-thead > tr > th) {
  background: var(--cp-bg);
  color: var(--cp-text-soft);
  font-size: 11px;
  font-weight: 600;
}

.orders-table-panel :deep(.ant-table-tbody > tr > td) {
  border-bottom-color: #eff0f2;
  font-size: 12px;
}

.orders-table-panel :deep(.ant-table-tbody > tr > td:last-child .ant-btn + .ant-btn) {
  margin-left: 8px;
}

/* 从 ?orderNo= 进入时高亮该笔订单 */
.orders-table-panel :deep(.order-row-highlight > td) {
  background: var(--cp-status-pending-bg) !important;
}

.order-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}

.order-status {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border: 1px solid transparent;
  border-radius: 999px;
  font-size: 11px;
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

.order-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.order-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-surface);
  box-shadow: 0 8px 24px rgba(17, 24, 39, 0.04);
}

.order-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.order-card-meta {
  color: var(--cp-text-soft);
  font-size: 13px;
}

.order-card-actions {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}

@media (max-width: 767px) {
  .toolbar-count {
    margin-left: 0;
  }
}
</style>