<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { Button, Input, Pagination, Select, Skeleton, Table } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import type { OrderStatus, PaymentOrder, Tier } from '../api/types'
import { listAdminOrders } from '../api/admin-payment'
import { errorMessage } from '../api/http'
import { formatDateTime } from '../utils/format'
import {
  ORDER_STATUS_OPTIONS,
  TIER_FILTER_OPTIONS,
  channelText,
  formatPrice,
  orderStatusText,
  orderStatusTone,
} from '../utils/payment'
import AppBreadcrumb from '../components/AppBreadcrumb.vue'
import EmptyState from '../components/EmptyState.vue'
import ErrorState from '../components/ErrorState.vue'

const DEFAULT_PAGE_SIZE = 10

const route = useRoute()
const router = useRouter()

const crumbs = [{ label: '首页' }, { label: '订单管理' }]

const orders = ref<PaymentOrder[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref<string | null>(null)

const userIdInput = ref('')
const statusInput = ref<OrderStatus | undefined>(undefined)
const tierInput = ref<Tier | undefined>(undefined)

let requestSeq = 0

function readQuery() {
  const asString = (value: unknown) => (typeof value === 'string' && value ? value : undefined)
  const current = Number(route.query.current) > 0 ? Number(route.query.current) : 1
  const size = Number(route.query.size) > 0 ? Number(route.query.size) : DEFAULT_PAGE_SIZE
  return {
    current,
    size,
    userId: asString(route.query.userId),
    status: asString(route.query.status) as OrderStatus | undefined,
    tier: asString(route.query.tier) as Tier | undefined,
  }
}

const query = computed(() => readQuery())

async function load() {
  const { current, size, userId, status, tier } = readQuery()
  const seq = ++requestSeq
  loading.value = true
  error.value = null
  try {
    const page = await listAdminOrders({ current, size, userId, status, tier })
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
  if (merged.userId) next.userId = merged.userId
  if (merged.status) next.status = merged.status
  if (merged.tier) next.tier = merged.tier
  if (merged.current > 1) next.current = merged.current
  if (merged.size !== DEFAULT_PAGE_SIZE) next.size = merged.size
  router.push({ name: 'admin-payment-orders', query: next })
}

function submitSearch() {
  pushQuery({
    userId: userIdInput.value.trim() || undefined,
    status: statusInput.value,
    tier: tierInput.value,
    current: 1,
  })
}

function refresh() {
  load()
}

function changePage(page: number, size: number) {
  pushQuery({ current: page, size })
}

const columns: TableColumnsType = [
  { title: '订单编号', key: 'orderNo', width: 200 },
  { title: '用户 ID', key: 'userId', width: 180 },
  { title: '套餐', key: 'tier', width: 80 },
  { title: '金额', key: 'amount', width: 90 },
  { title: '状态', key: 'status', width: 100 },
  { title: '渠道', key: 'channel', width: 90 },
  { title: '交易号', key: 'transactionId', width: 220 },
  { title: '创建时间', key: 'createTime', width: 150 },
  { title: '支付时间', key: 'payTime', width: 150 },
]

// 窄屏用纵向卡片替代表格，避免横向滚动
const narrow = ref(false)
const mediaQuery = window.matchMedia('(max-width: 767px)')
function syncNarrow(event: MediaQueryList | MediaQueryListEvent) {
  narrow.value = event.matches
}
syncNarrow(mediaQuery)
onMounted(() => mediaQuery.addEventListener('change', syncNarrow))
onUnmounted(() => mediaQuery.removeEventListener('change', syncNarrow))

watch(
  () => route.query,
  () => {
    const current = readQuery()
    userIdInput.value = current.userId ?? ''
    statusInput.value = current.status
    tierInput.value = current.tier
    load()
  },
  { immediate: true },
)
</script>

<template>
  <div class="cp-container cp-page admin-orders-page">
    <AppBreadcrumb :items="crumbs" />

    <div class="admin-orders-head">
      <div>
        <p class="section-kicker">PAYMENT GOVERNANCE · 订单流水</p>
        <h1 class="cp-page-title">订单管理</h1>
        <p class="cp-page-subtitle">查看全部套餐订单流水与交易号，管理端只读</p>
      </div>
      <span class="admin-badge">管理员视角</span>
    </div>

    <div class="readonly-note">
      管理端仅查看订单流水与元信息，不提供退款 / 关单 / 修改套餐等操作。
    </div>

    <div class="cp-toolbar admin-orders-toolbar">
      <Input
        v-model:value="userIdInput"
        placeholder="用户 ID"
        allow-clear
        style="width: 180px"
        @press-enter="submitSearch"
      />
      <Select
        v-model:value="statusInput"
        :options="ORDER_STATUS_OPTIONS"
        placeholder="全部状态"
        allow-clear
        style="width: 150px"
      />
      <Select
        v-model:value="tierInput"
        :options="TIER_FILTER_OPTIONS"
        placeholder="全部档位"
        allow-clear
        style="width: 130px"
      />
      <Button type="primary" @click="submitSearch">查询</Button>
      <Button @click="refresh">刷新</Button>
      <span class="toolbar-count">共 {{ total }} 条订单流水</span>
    </div>

    <Skeleton v-if="loading" :paragraph="{ rows: 6 }" active />

    <ErrorState v-else-if="error" message="订单流水加载失败" :description="error" @retry="load" />

    <EmptyState v-else-if="!orders.length" description="没有符合条件的订单流水" />

    <div v-else-if="!narrow" class="admin-orders-panel">
      <Table
        :columns="columns"
        :data-source="orders"
        :pagination="false"
        :row-key="(record: PaymentOrder) => record.orderNo"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'orderNo'">
            <span class="order-mono">{{ record.orderNo }}</span>
          </template>
          <template v-else-if="column.key === 'userId'">
            <span class="order-mono">{{ record.userId }}</span>
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
          <template v-else-if="column.key === 'channel'">
            {{ channelText(record.channel) }}
          </template>
          <template v-else-if="column.key === 'transactionId'">
            <span class="order-mono">{{ record.transactionId ?? '—' }}</span>
          </template>
          <template v-else-if="column.key === 'createTime'">
            {{ formatDateTime(record.createTime) }}
          </template>
          <template v-else-if="column.key === 'payTime'">
            {{ formatDateTime(record.payTime) }}
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
          {{ order.tierName }} · {{ formatPrice(order.amountFen) }} · {{ channelText(order.channel) }}
        </div>
        <div class="order-card-meta">用户 ID：{{ order.userId }}</div>
        <div class="order-card-meta">交易号：{{ order.transactionId ?? '—' }}</div>
        <div class="order-card-meta">创建时间：{{ formatDateTime(order.createTime) }}</div>
        <div class="order-card-meta">支付时间：{{ formatDateTime(order.payTime) }}</div>
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
  </div>
</template>

<style scoped>
.admin-orders-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.admin-orders-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-top: 4px;
}

.admin-badge {
  display: inline-flex;
  align-items: center;
  padding: 3px 12px;
  border-radius: 999px;
  background: var(--cp-accent);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}

.readonly-note {
  padding: 10px 14px;
  border: 1px solid var(--cp-status-pending-border);
  border-radius: var(--cp-radius);
  background: var(--cp-status-pending-bg);
  color: var(--cp-status-pending-fg);
  font-size: 12px;
}

.admin-orders-toolbar {
  margin-top: 0;
  padding: 12px;
  border: 1px solid var(--cp-border-subtle);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-surface);
}

.toolbar-count {
  margin-left: auto;
  color: var(--cp-text-soft);
  font-size: 13px;
}

.admin-orders-panel {
  overflow: hidden;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius-lg);
  background: #fff;
  box-shadow: 0 8px 24px rgba(17, 24, 39, 0.04);
}

.admin-orders-panel :deep(.ant-table) {
  background: transparent;
}

.admin-orders-panel :deep(.ant-table-thead > tr > th) {
  background: var(--cp-bg);
  color: var(--cp-text-soft);
  font-size: 11px;
  font-weight: 600;
}

.admin-orders-panel :deep(.ant-table-tbody > tr > td) {
  border-bottom-color: #eff0f2;
  font-size: 12px;
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
  gap: 6px;
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

@media (max-width: 767px) {
  .admin-orders-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .toolbar-count {
    margin-left: 0;
  }
}
</style>