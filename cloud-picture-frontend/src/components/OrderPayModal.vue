<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { Modal, message } from 'ant-design-vue'
import type { PaymentOrder } from '../api/types'
import { getOrder, mockPay } from '../api/payment'
import { errorMessage } from '../api/http'
import { channelPayText, createCountdown, formatPrice } from '../utils/payment'

/** 支付步骤弹窗：套餐页 / 订单列表 / 订单详情三处共用。
 *  仅承载 MOCK 通道（alipay 由调用方新窗口跳收银台），故按钮固定为「模拟支付成功」。 */
const open = defineModel<boolean>('open', { required: true })
const props = defineProps<{ order: PaymentOrder | null }>()
const emit = defineEmits<{ paid: [order: PaymentOrder]; closed: [] }>()

const paying = ref(false)
/** 本地副本：倒计时归零后需要把刷新到的 CLOSED 状态落到界面，不能直接改 prop */
const currentOrder = ref<PaymentOrder | null>(null)
const { remainingText, start, stop } = createCountdown()

const orderClosed = computed(() => currentOrder.value?.status === 'CLOSED')

watch(
  () => [open.value, props.order] as const,
  ([isOpen]) => {
    if (!isOpen) {
      stop()
      return
    }
    currentOrder.value = props.order
    // 倒计时归零后后端懒关单，重新取一次即可拿到 CLOSED
    start(props.order?.expireTime ?? null, () => {
      const orderNo = currentOrder.value?.orderNo
      if (!orderNo) return
      getOrder(orderNo)
        .then((fresh) => (currentOrder.value = fresh))
        .catch(() => undefined)
    })
  },
  { immediate: true },
)

async function payMock() {
  const current = currentOrder.value
  if (!current || paying.value || current.status !== 'UNPAID') return
  paying.value = true
  try {
    await mockPay(current.orderNo)
    // 后端 markPaid 在同一次请求内提交，取一次最终状态即可，无需轮询
    const settled = await getOrder(current.orderNo)
    if (settled.status !== 'PAID') {
      message.warning('支付已提交，请在订单列表查看结果')
      open.value = false
      emit('closed')
      return
    }
    open.value = false
    emit('paid', settled)
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    paying.value = false
  }
}

function onCancel() {
  emit('closed')
}

onUnmounted(stop)
</script>

<template>
  <Modal
    v-model:open="open"
    title="完成支付"
    :mask-closable="!paying"
    :confirm-loading="paying"
    ok-text="模拟支付成功"
    cancel-text="取消"
    :ok-button-props="{ disabled: orderClosed }"
    @ok="payMock"
    @cancel="onCancel"
  >
    <div class="pay-body">
      <p class="pay-countdown" :class="{ 'is-closed': orderClosed }">
        {{ orderClosed ? '订单已关闭' : `请在 ${remainingText} 内完成支付` }}
      </p>

      <dl class="pay-list">
        <div>
          <dt>套餐</dt>
          <dd>{{ currentOrder?.tierName ?? '—' }}</dd>
        </div>
        <div>
          <dt>金额</dt>
          <dd>{{ currentOrder ? formatPrice(currentOrder.amountFen) : '—' }}</dd>
        </div>
        <div>
          <dt>支付方式</dt>
          <dd>{{ currentOrder ? channelPayText(currentOrder.channel) : '—' }}</dd>
        </div>
      </dl>
    </div>
  </Modal>
</template>

<style scoped>
.pay-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.pay-countdown {
  margin: 0;
  padding: 8px 12px;
  border-radius: var(--cp-radius);
  background: var(--cp-status-pending-bg);
  color: var(--cp-status-pending-fg);
  font-size: 12px;
  font-weight: 600;
}

.pay-countdown.is-closed {
  background: var(--cp-bg-soft);
  color: var(--cp-text-soft);
}

.pay-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 0;
}

.pay-list div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.pay-list dt {
  color: var(--cp-text-soft);
  font-size: 12px;
}

.pay-list dd {
  margin: 0;
  color: var(--cp-text);
  font-size: 13px;
  font-weight: 600;
}
</style>