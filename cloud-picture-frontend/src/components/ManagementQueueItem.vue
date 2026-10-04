<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { Id, ImageVO } from '../api/types'
import { REVIEW_STATUS_TEXT } from '../stores/ui'
import { formatRelativeTime, formatSize } from '../utils/format'

const props = defineProps<{
  image: ImageVO
  mode: 'review' | 'mine'
  selected?: boolean
}>()

const emit = defineEmits<{ select: [Id] }>()

const broken = ref(false)
watch(
  () => props.image.thumbnailUrl,
  () => (broken.value = false),
)

const thumbUrl = computed(() => props.image.thumbnailUrl || props.image.url || '')
const visibleTags = computed(() => (props.image.tags ?? []).slice(0, 3))
const spec = computed(() => `${(props.image.picFormat || 'IMAGE').toUpperCase()} · ${formatSize(props.image.picSize)}`)

/** 约分后的比例徽标；约分后仍过大（非常见比例）则不显示 */
const ratioLabel = computed(() => {
  const w = props.image.picWidth
  const h = props.image.picHeight
  if (!w || !h) return ''
  const divisor = gcd(w, h)
  const rw = w / divisor
  const rh = h / divisor
  return rw <= 30 && rh <= 30 ? `${rw}:${rh}` : ''
})

function gcd(a: number, b: number): number {
  return b === 0 ? a : gcd(b, a % b)
}
</script>

<template>
  <button
    type="button"
    class="queue-item"
    :class="{ 'queue-item-active': selected }"
    :aria-pressed="selected ? 'true' : 'false'"
    @click="emit('select', image.id)"
  >
    <span class="queue-item-media">
      <img
        v-if="thumbUrl && !broken"
        :src="thumbUrl"
        :alt="image.name || '图片预览'"
        loading="lazy"
        decoding="async"
        @error="broken = true"
      />
      <span v-else class="queue-item-fallback">图片加载失败</span>
      <span v-if="ratioLabel" class="queue-item-ratio">{{ ratioLabel }}</span>
    </span>

    <span class="queue-item-body">
      <span class="queue-item-main">
        <span class="queue-item-heading">
          <span class="queue-item-title">{{ image.name || '未命名图片' }}</span>
          <span class="queue-item-status" :class="`queue-item-status-${image.reviewStatus}`">
            {{ REVIEW_STATUS_TEXT[image.reviewStatus] }}
          </span>
        </span>
        <span class="queue-item-sub">
          <span v-if="mode === 'review'" class="queue-item-owner">
            {{ image.owner?.name || '未知用户' }}
          </span>
          <span v-else class="queue-item-owner">{{ image.category || '' }}</span>
          <span class="queue-item-spec">{{ spec }}</span>
        </span>
      </span>

      <span class="queue-item-foot">
        <span class="queue-item-tags">
          <span v-for="tag in visibleTags" :key="tag" class="queue-item-tag">#{{ tag }}</span>
        </span>
        <span class="queue-item-time">{{ formatRelativeTime(image.createTime) }}</span>
      </span>
    </span>
  </button>
</template>

<style scoped>
.queue-item {
  position: relative;
  display: flex;
  width: 100%;
  gap: 14px;
  padding: 14px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-surface);
  box-shadow: var(--cp-shadow-subtle);
  overflow: hidden;
  cursor: pointer;
  font: inherit;
  text-align: left;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.queue-item:hover {
  box-shadow: var(--cp-shadow-card);
}

.queue-item-active {
  border-color: rgba(17, 24, 39, 0.2);
  box-shadow: var(--cp-shadow-card);
}

.queue-item-active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3.5px;
  background: var(--cp-accent);
}

.queue-item-media {
  position: relative;
  display: block;
  width: 108px;
  height: 108px;
  flex: none;
  border-radius: var(--cp-radius);
  overflow: hidden;
  background: #111827;
}

.queue-item-media img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.3s ease;
}

.queue-item:hover .queue-item-media img {
  transform: scale(1.05);
}

.queue-item-fallback {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  color: #fff;
  font-size: 11px;
}

.queue-item-ratio {
  position: absolute;
  right: 4px;
  bottom: 4px;
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.7);
  color: #fff;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 10px;
  line-height: 1.5;
}

.queue-item-body {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  justify-content: space-between;
  gap: 8px;
  padding: 2px 0;
}

.queue-item-main {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
}

.queue-item-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.queue-item-title {
  min-width: 0;
  overflow: hidden;
  color: var(--cp-text);
  font-size: 14px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.queue-item-status {
  flex: none;
  padding: 2px 8px;
  border: 1px solid transparent;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 600;
}

.queue-item-status-0 {
  background: var(--cp-status-pending-bg);
  color: var(--cp-status-pending-fg);
  border-color: var(--cp-status-pending-border);
}

.queue-item-status-1 {
  background: var(--cp-status-approved-bg);
  color: var(--cp-status-approved-fg);
  border-color: var(--cp-status-approved-border);
}

.queue-item-status-2 {
  background: var(--cp-status-rejected-bg);
  color: var(--cp-status-rejected-fg);
  border-color: var(--cp-status-rejected-border);
}

.queue-item-sub {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--cp-text-soft);
  font-size: 12px;
}

.queue-item-owner {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.queue-item-spec {
  flex: none;
  color: var(--cp-text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
}

.queue-item-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--cp-border);
}

.queue-item-tags {
  display: flex;
  min-width: 0;
  gap: 4px;
  overflow: hidden;
}

.queue-item-tag {
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--cp-bg-soft);
  color: var(--cp-text-soft);
  font-size: 10px;
  white-space: nowrap;
}

.queue-item-time {
  flex: none;
  color: var(--cp-text-muted);
  font-size: 11px;
}

@media (max-width: 575px) {
  .queue-item-media {
    width: 96px;
    height: 96px;
  }
}
</style>