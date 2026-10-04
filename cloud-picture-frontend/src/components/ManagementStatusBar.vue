<script setup lang="ts">
/**
 * 状态筛选条（唯一权威样式）：浅灰容器 + 选中项白底胶囊 + 橙色粗等宽计数（选中）/ 灰色等宽（未选）。
 * 供「图片审核」与「我的上传」共用，切勿改回实心黑胶囊 / 彩色徽标 / 两行卡片。
 */
defineProps<{
  modelValue: string
  options: { value: string; label: string; count: number | string }[]
}>()

const emit = defineEmits<{ 'update:modelValue': [string] }>()
</script>

<template>
  <div class="status-bar" role="radiogroup">
    <button
      v-for="option in options"
      :key="option.value"
      type="button"
      role="radio"
      :aria-checked="modelValue === option.value"
      class="status-bar-item"
      :class="{ 'status-bar-item-active': modelValue === option.value }"
      @click="emit('update:modelValue', option.value)"
    >
      <span class="status-bar-label">{{ option.label }}</span>
      <span class="status-bar-count">{{ option.count }}</span>
    </button>
  </div>
</template>

<style scoped>
.status-bar {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  padding: 4px;
  border-radius: var(--cp-radius-lg);
  background: var(--cp-bg-soft);
  overflow-x: auto;
}

.status-bar-item {
  display: inline-flex;
  flex: none;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  border: 1px solid transparent;
  border-radius: var(--cp-radius);
  background: transparent;
  color: var(--cp-text-soft);
  font: inherit;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: color 0.15s ease, background 0.15s ease;
}

.status-bar-item:hover {
  color: var(--cp-text);
}

.status-bar-item-active {
  background: var(--cp-surface);
  border-color: var(--cp-border);
  color: var(--cp-text);
  font-weight: 700;
  box-shadow: var(--cp-shadow-subtle);
}

.status-bar-count {
  color: var(--cp-text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}

.status-bar-item-active .status-bar-count {
  color: var(--cp-status-pending-fg);
  font-weight: 700;
}
</style>