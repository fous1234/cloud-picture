<script setup lang="ts">
import type { SpaceVO } from '../api/types'
import { formatSize } from '../utils/format'

defineProps<{ space: SpaceVO }>()
const emit = defineEmits<{
  rename: []
  remove: []
}>()
</script>

<template>
  <section class="summary-card">
    <header class="summary-head">
      <div class="summary-identity">
        <span class="summary-icon" aria-hidden="true">
          <svg fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
            <path d="M4 20h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.93a2 2 0 0 1-1.66-.9l-.82-1.2A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13c0 1.1.9 2 2 2Z"></path>
            <path d="M12 10v6"></path>
            <path d="m9 13 3-3 3 3"></path>
          </svg>
        </span>
        <div class="summary-copy">
          <div class="summary-title-row">
            <h2 class="summary-title" :title="space.name">{{ space.name }}</h2>
            <button class="summary-link" type="button" @click="emit('rename')">改名</button>
            <button class="summary-link summary-link-danger" type="button" @click="emit('remove')">
              删除空间
            </button>
          </div>
        </div>
      </div>
    </header>

    <div class="summary-metrics">
      <div class="summary-metric">
        <div class="summary-metric-head">
          <span class="summary-metric-label">图片数量</span>
          <span class="summary-metric-value">
            {{ space.imageCount }} 张
          </span>
        </div>
      </div>

      <div class="summary-metric">
        <div class="summary-metric-head">
          <span class="summary-metric-label">存储容量</span>
          <span class="summary-metric-value">
            {{ formatSize(space.totalSize) }}
          </span>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.summary-card {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 20px 24px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-surface);
  box-shadow: var(--cp-shadow-subtle);
}

.summary-head {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.summary-identity {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.summary-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  flex: none;
  border-radius: var(--cp-radius);
  background: var(--cp-accent);
  color: #fff;
}

.summary-icon svg {
  width: 24px;
  height: 24px;
}

.summary-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
}

.summary-title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.summary-title {
  overflow: hidden;
  margin: 0;
  color: var(--cp-text);
  font-size: 20px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.summary-link {
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

.summary-link:hover {
  border-color: var(--cp-border);
  background: var(--cp-bg-soft);
  color: var(--cp-text);
}

.summary-link-danger {
  color: #cf1322;
}

.summary-link-danger:hover {
  border-color: transparent;
  background: var(--cp-status-rejected-bg);
  color: #cf1322;
}

.summary-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 24px;
}

.summary-metric {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.summary-metric-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.summary-metric-label {
  color: var(--cp-text-soft);
  font-size: 12px;
}

.summary-metric-value {
  color: var(--cp-text);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  font-weight: 600;
}

@media (min-width: 576px) {
  .summary-head {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }
}

@media (max-width: 575px) {
  .summary-metrics {
    grid-template-columns: 1fr;
  }
}
</style>
