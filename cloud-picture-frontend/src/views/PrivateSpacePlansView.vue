<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Alert, Button, Modal, Result, Skeleton, message } from 'ant-design-vue'
import { CheckOutlined } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import type { RouteLocationRaw } from 'vue-router'
import type { PaymentOrder, PlanList, SpacePlan, SpaceVO } from '../api/types'
import { getMySpace } from '../api/space'
import { createOrder, getPlans } from '../api/payment'
import { ApiError, errorMessage } from '../api/http'
import { formatDateTime, formatSize } from '../utils/format'
import {
  PLAN_DAYS,
  formatPrice,
  mbText,
  planAction,
  planBenefits,
  planDesc,
  planPeriodText,
  planPriceText,
  submitRedirectForm,
} from '../utils/payment'
import AppBreadcrumb from '../components/AppBreadcrumb.vue'
import ErrorState from '../components/ErrorState.vue'
import OrderPayModal from '../components/OrderPayModal.vue'

const router = useRouter()

const crumbs: { label: string; to?: RouteLocationRaw; back?: boolean }[] = [
  { label: '首页', to: { name: 'gallery' } },
  { label: '私有空间', back: true },
  { label: '升级套餐' },
]

const space = ref<SpaceVO | null>(null)
const planList = ref<PlanList | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)

/** 确认订单弹窗：只看明细并建单，支付步骤交给 OrderPayModal */
const confirmOpen = ref(false)
const selectedPlan = ref<SpacePlan | null>(null)
const creating = ref(false)

/** 支付步骤弹窗（MOCK；alipay 直接跳收银台，不经过它） */
const payOpen = ref(false)
const payEntry = ref<PaymentOrder | null>(null)

/** 支付结果弹窗：仅 MOCK 通道出现 */
const resultOpen = ref(false)
const resultOrder = ref<PaymentOrder | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    const [mine, plans] = await Promise.all([getMySpace(), getPlans()])
    space.value = mine
    planList.value = plans
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

/** 卡片 + 按钮三态：一次性算好，避免模板里重复推导 */
const cards = computed(() =>
  (planList.value?.plans ?? []).map((plan) => ({
    plan,
    action: planAction(plan, planList.value?.spaceCreated ?? false),
  })),
)

function goOrders(orderNo?: string) {
  router.push({ name: 'private-space-orders', query: orderNo ? { orderNo } : {} })
}

function goPrivateSpace() {
  router.push({ name: 'private-space' })
}

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

/** 预计到期时间预览：与后端 grantPlan 同口径（max(now, 当前到期) + PLAN_DAYS 天） */
const previewExpireText = computed(() => {
  const current = planList.value?.expireTime
  const base = current ? new Date(current.replace(' ', 'T')).getTime() : 0
  const from = Math.max(Date.now(), Number.isNaN(base) ? 0 : base)
  const date = new Date(from + PLAN_DAYS * 24 * 60 * 60 * 1000)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
})

function openConfirm(plan: SpacePlan) {
  selectedPlan.value = plan
  confirmOpen.value = true
}

function closeConfirm() {
  confirmOpen.value = false
  selectedPlan.value = null
}

async function confirmOrder() {
  const plan = selectedPlan.value
  if (!plan || creating.value) return
  creating.value = true
  try {
    const created = await createOrder(plan.tier)
    closeConfirm()
    if (created.mockPay) {
      payEntry.value = created
      payOpen.value = true
      return
    }
    // alipay：新窗口开收银台，当前页跳订单列表开始轮询
    const opened = created.redirectForm ? submitRedirectForm(created.redirectForm) : false
    if (!opened) {
      message.warning('支付窗口被浏览器拦截，可在「我的订单」继续支付')
    }
    goOrders(created.orderNo)
  } catch (e) {
    message.error(errorMessage(e))
    // 未创建空间时下单一律被后端拒绝，兜底把用户带回私有空间页
    if (e instanceof ApiError && e.code === 'PLAN_NOT_PURCHASABLE' && !space.value) {
      closeConfirm()
      goPrivateSpace()
    }
  } finally {
    creating.value = false
  }
}

function onPaid(order: PaymentOrder) {
  payEntry.value = null
  resultOrder.value = order
  resultOpen.value = true
}

function finishResult() {
  const orderNo = resultOrder.value?.orderNo
  resultOrder.value = null
  resultOpen.value = false
  goOrders(orderNo)
}

onMounted(load)
</script>

<template>
  <div class="cp-container cp-page plans-page">
    <AppBreadcrumb :items="crumbs" />

    <div class="plans-head">
      <p class="section-kicker">MEMBERSHIP &amp; CAPACITY</p>
      <h1 class="cp-page-title">升级套餐</h1>
      <p class="cp-page-subtitle">
        升级后立即生效 · 30 天有效期 · 到期自动降回普通，已上传图片不会被删除
      </p>
    </div>

    <Skeleton v-if="loading" :paragraph="{ rows: 8 }" active />

    <ErrorState v-else-if="error" message="套餐信息加载失败" :description="error" @retry="load" />

    <template v-else>
      <!-- 区块 A：我的用量（只读，三列） -->
      <section v-if="space" class="usage-card">
        <div class="usage-item">
          <span class="usage-label">当前套餐</span>
          <span class="usage-value">
            {{ space.tierName }}
            <em v-if="space.tier !== 'FREE'" class="usage-sub">
              · {{ formatDateTime(space.tierExpireTime) }} 到期
            </em>
          </span>
        </div>
        <div class="usage-item">
          <span class="usage-label">已用</span>
          <span class="usage-value">
            {{ formatSize(space.totalSize) }} / {{ mbText(space.sizeLimitBytes) }}
          </span>
        </div>
        <div class="usage-item">
          <span class="usage-label">图片</span>
          <span class="usage-value">{{ space.imageCount }} / {{ space.imageLimit }} 张</span>
        </div>
      </section>

      <Alert
        v-else
        class="usage-empty"
        type="warning"
        show-icon
        message="你还没有创建私有空间"
        description="套餐是私有空间的扩容权益，请先创建私有空间后再升级。"
      >
        <template #action>
          <Button size="small" @click="goPrivateSpace">返回私有空间</Button>
        </template>
      </Alert>

      <!-- 区块 B：套餐卡片 -->
      <section class="plan-grid">
        <article
          v-for="card in cards"
          :key="card.plan.tier"
          class="plan-card"
          :class="{ 'is-current': card.plan.current }"
        >
          <header class="plan-card-head">
            <h2 class="plan-name">{{ card.plan.name }}</h2>
            <span
              v-if="card.plan.badge"
              class="plan-badge"
              :class="{ 'is-recommend': !card.plan.current }"
            >
              {{ card.plan.badge }}
            </span>
          </header>

          <p class="plan-desc">{{ planDesc(card.plan) }}</p>

          <p class="plan-price">
            {{ planPriceText(card.plan) }}
            <span class="plan-period">{{ planPeriodText(card.plan) }}</span>
          </p>

          <Button
            class="plan-button"
            block
            :type="card.action.kind === 'primary' ? 'primary' : 'default'"
            :disabled="card.action.kind === 'disabled'"
            @click="openConfirm(card.plan)"
          >
            {{ card.action.text }}
          </Button>

          <ul class="plan-benefits">
            <li v-for="benefit in planBenefits(card.plan)" :key="benefit">
              <CheckOutlined />
              <span>{{ benefit }}</span>
            </li>
          </ul>
        </article>
      </section>

      <!-- 区块 C：说明 -->
      <section class="plan-rules">
        <h2 class="plan-rules-title">升级须知与退订规则</h2>
        <ul>
          <li>升级为一次性购买 30 天，不与旧套餐折算</li>
          <li>到期后自动降回普通，已上传图片不会被删除，只是不能再上传新图片</li>
          <li>未支付订单 30 分钟内有效，超时自动关闭</li>
        </ul>
      </section>
    </template>

    <!-- 弹窗 D：订单确认（建单；支付步骤在 OrderPayModal） -->
    <Modal
      v-model:open="confirmOpen"
      title="确认订单"
      :mask-closable="!creating"
      :confirm-loading="creating"
      ok-text="确认支付"
      cancel-text="取消"
      @ok="confirmOrder"
      @cancel="closeConfirm"
    >
      <div v-if="selectedPlan" class="confirm-body">
        <dl class="confirm-list">
          <div><dt>套餐</dt><dd>{{ selectedPlan.name }}</dd></div>
          <div><dt>金额</dt><dd>{{ formatPrice(selectedPlan.priceFen) }}</dd></div>
          <div><dt>有效期</dt><dd>{{ PLAN_DAYS }} 天</dd></div>
          <div><dt>预计到期时间</dt><dd>{{ previewExpireText }}</dd></div>
        </dl>
        <p class="confirm-tip">确认后将创建订单，支付步骤在下一步完成。</p>
      </div>
    </Modal>

    <!-- 支付步骤（MOCK 通道） -->
    <OrderPayModal v-model:open="payOpen" :order="payEntry" @paid="onPaid" />

    <!-- 弹窗 E：支付结果（仅 MOCK） -->
    <Modal
      v-model:open="resultOpen"
      :footer="null"
      :closable="false"
      :keyboard="false"
      :mask-closable="false"
      :width="440"
    >
      <Result
        status="success"
        title="支付成功"
        :sub-title="`${resultOrder?.tierName ?? ''} 套餐已生效，有效期至 ${formatDateTime(resultOrder?.tierExpireTime)}`"
      >
        <template #extra>
          <p class="result-order-no">订单编号：{{ resultOrder?.orderNo }}</p>
          <Button type="primary" @click="finishResult">查看订单</Button>
        </template>
      </Result>
    </Modal>
  </div>
</template>

<style scoped>
.plans-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.plans-head {
  margin-top: 4px;
}

/* 区块 A：我的用量 */
.usage-card {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  padding: 20px 24px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-surface);
  box-shadow: var(--cp-shadow-subtle);
}

.usage-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.usage-label {
  color: var(--cp-text-muted);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.usage-value {
  color: var(--cp-text);
  font-size: 15px;
  font-weight: 600;
}

.usage-sub {
  color: var(--cp-text-soft);
  font-size: 12px;
  font-style: normal;
  font-weight: 500;
}

.usage-empty {
  margin: 0;
}

/* 区块 B：套餐卡片 */
.plan-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.plan-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-surface);
  box-shadow: var(--cp-shadow-subtle);
}

.plan-card.is-current {
  border-color: var(--cp-accent);
}

.plan-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.plan-name {
  margin: 0;
  color: var(--cp-text);
  font-size: 18px;
  font-weight: 600;
}

.plan-badge {
  padding: 2px 10px;
  border: 1px solid var(--cp-border);
  border-radius: 999px;
  background: var(--cp-bg-soft);
  color: var(--cp-text-soft);
  font-size: 11px;
  font-weight: 600;
}

.plan-badge.is-recommend {
  border-color: var(--cp-accent);
  background: var(--cp-accent);
  color: #fff;
}

.plan-desc {
  margin: 0;
  min-height: 36px;
  color: var(--cp-text-soft);
  font-size: 12px;
  line-height: 18px;
}

.plan-price {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin: 0;
  color: var(--cp-text);
  font-size: 28px;
  font-weight: 700;
}

.plan-period {
  color: var(--cp-text-muted);
  font-size: 12px;
  font-weight: 500;
}

.plan-button {
  height: 40px;
  border-radius: var(--cp-radius);
  font-weight: 600;
}

.plan-benefits {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.plan-benefits li {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--cp-text-soft);
  font-size: 12px;
}

.plan-benefits li :deep(.anticon) {
  color: var(--cp-status-approved-fg);
}

/* 区块 C：说明 */
.plan-rules {
  padding: 20px 24px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-bg-soft);
}

.plan-rules-title {
  margin: 0 0 10px;
  color: var(--cp-text);
  font-size: 14px;
  font-weight: 600;
}

.plan-rules ul {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
  padding-left: 18px;
  color: var(--cp-text-soft);
  font-size: 12px;
  line-height: 18px;
}

/* 确认弹窗 */
.confirm-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.confirm-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 0;
}

.confirm-list div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.confirm-list dt {
  color: var(--cp-text-soft);
  font-size: 12px;
}

.confirm-list dd {
  margin: 0;
  color: var(--cp-text);
  font-size: 13px;
  font-weight: 600;
}

.confirm-tip {
  margin: 0;
  color: var(--cp-text-muted);
  font-size: 12px;
}

.result-order-no {
  margin: 0 0 12px;
  color: var(--cp-text-soft);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}

@media (max-width: 767px) {
  .usage-card,
  .plan-grid {
    grid-template-columns: 1fr;
  }
}
</style>