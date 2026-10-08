<script setup lang="ts">
import { computed } from 'vue'
import { Button } from 'ant-design-vue'
import type { SpaceVO } from '../api/types'
import { formatDateTime, formatSize } from '../utils/format'
import {
  QUOTA_FULL_TEXT,
  mbText,
  quotaFull,
  quotaPercent,
  quotaReached,
} from '../utils/payment'

const props = defineProps<{ space: SpaceVO }>()
const emit = defineEmits<{
  upgrade: []
  orders: []
  rename: []
  remove: []
}>()

const isFree = computed(() => props.space.tier === 'FREE')
/** MAX 已是最高档，不再显示升级入口 */
const canUpgrade = computed(() => props.space.tier !== 'MAX')

const imageFull = computed(() => quotaReached(props.space.imageCount, props.space.imageLimit))
const sizeFull = computed(() => quotaReached(props.space.totalSize, props.space.sizeLimitBytes))
const full = computed(() => quotaFull(props.space))

const imageWidth = computed(
  () => `${quotaPercent(props.space.imageCount, props.space.imageLimit)}%`,
)
const sizeWidth = computed(
  () => `${quotaPercent(props.space.totalSize, props.space.sizeLimitBytes)}%`,
)
</script>

<template>
  <section class="quota-card">
    <header class="quota-head">
      <div class="quota-identity">
        <span class="quota-icon" aria-hidden="true">
          <svg fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path d="M4 20h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.93a2 2 0 0 1-1.66-.9l-.82-1.2A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13c0 1.1.9 2 2 2Z"></path>
            <path d="M12 10v6"></path>
            <path d="m9 13 3-3 3 3"></path>
          </svg>
        </span>
        <div class="quota-copy">
          <div class="quota-title-row">
            <h2 class="quota-title" :title="space.name">{{ space.name }}</h2>
            <button class="quota-link" type="button" @click="emit('rename')">改名</button>
            <button class="quota-link quota-link-danger" type="button" @click="emit('remove')">
              删除空间
            </button>
          </div>
          <p class="quota-stat">共 {{ space.imageCount }} 张 · 占用 {{ formatSize(space.totalSize) }}</p>
          <p v-if="!isFree" class="quota-member">
            {{ space.tierName }} 会员 · {{ formatDateTime(space.tierExpireTime) }} 到期
          </p>
        </div>
      </div>

      <div class="quota-actions">
        <span v-if="full" class="quota-alert">{{ QUOTA_FULL_TEXT }}</span>
        <Button v-if="canUpgrade" type="primary" @click="emit('upgrade')">升级套餐</Button>
        <Button @click="emit('orders')">我的订单</Button>
      </div>
    </header>

    <div class="quota-metrics">
      <div class="quota-metric">
        <div class="quota-metric-head">
          <span class="quota-metric-label">图片数量</span>
          <span class="quota-metric-value" :class="{ 'is-full': imageFull }">
            {{ space.imageCount }} / {{ space.imageLimit }} 张
          </span>
        </div>
        <div class="quota-bar">
          <span class="quota-bar-fill" :class="{ 'is-full': imageFull }" :style="{ width: imageWidth }"></span>
        </div>
      </div>

      <div class="quota-metric">
        <div class="quota-metric-head">
          <span class="quota-metric-label">存储容量</span>
          <span class="quota-metric-value" :class="{ 'is-full': sizeFull }">
            {{ formatSize(space.totalSize) }} / {{ mbText(space.sizeLimitBytes) }}
          </span>
        </div>
        <div class="quota-bar">
          <span class="quota-bar-fill" :class="{ 'is-full': sizeFull }" :style="{ width: sizeWidth }"></span>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.quota-card {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 20px 24px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-surface);
  box-shadow: var(--cp-shadow-subtle);
}

.quota-head {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.quota-identity {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.quota-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  flex: none;
  border-radius: var(--cp-radius);
  background: var(--cp-accent);
  color: #fff;
}

.quota-icon svg {
  width: 24px;
  height: 24px;
}

.quota-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
}

.quota-title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.quota-title {
  overflow: hidden;
  margin: 0;
  color: var(--cp-text);
  font-size: 20px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quota-link {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--cp-text-soft);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  font-weight: 500;
}

.quota-link:hover {
  border-color: var(--cp-border);
  background: var(--cp-bg-soft);
  color: var(--cp-text);
}

.quota-link-danger {
  color: #cf1322;
}

.quota-link-danger:hover {
  border-color: transparent;
  background: var(--cp-status-rejected-bg);
  color: #cf1322;
}

.quota-stat {
  margin: 0;
  color: var(--cp-text-soft);
  font-size: 12px;
}

.quota-member {
  margin: 0;
  color: var(--cp-text-soft);
  font-size: 12px;
  font-weight: 500;
}

.quota-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.quota-alert {
  color: var(--cp-status-rejected-fg);
  font-size: 12px;
  font-weight: 600;
}

.quota-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 24px;
}

.quota-metric {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.quota-metric-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.quota-metric-label {
  color: var(--cp-text-soft);
  font-size: 12px;
}

.quota-metric-value {
  color: var(--cp-text);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  font-weight: 600;
}

.quota-metric-value.is-full {
  color: var(--cp-status-rejected-fg);
}

.quota-bar {
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--cp-bg-soft);
}

.quota-bar-fill {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--cp-accent);
  transition: width 0.2s ease;
}

.quota-bar-fill.is-full {
  background: var(--cp-status-rejected-fg);
}

@media (min-width: 576px) {
  .quota-head {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }
}

@media (max-width: 575px) {
  .quota-metrics {
    grid-template-columns: 1fr;
  }
}
</style>