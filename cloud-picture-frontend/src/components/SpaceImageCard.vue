<script setup lang="ts">
import { computed, ref } from 'vue'
import { Button, Tooltip } from 'ant-design-vue'
import { DeleteOutlined, DownloadOutlined } from '@ant-design/icons-vue'
import type { SpaceImageVO } from '../api/types'
import { formatRelativeTime, formatSize } from '../utils/format'

const props = defineProps<{
  image: SpaceImageVO
  selected?: boolean
}>()

const emit = defineEmits<{ select: []; download: []; delete: []; open: [] }>()

const broken = ref(false)

// 用后端返回的宽高占位，保持原始宽高比（不做统一裁切）；缺失时回落 4/3
const aspectRatio = computed(() => {
  const { picWidth, picHeight } = props.image
  return picWidth && picHeight ? `${picWidth} / ${picHeight}` : '4 / 3'
})

const visibleTags = computed(() => (props.image.tags ?? []).slice(0, 3))
</script>

<template>
  <article class="space-card" :class="{ 'space-card-selected': selected }" @click="emit('select')">
    <div class="space-card-media" :style="{ aspectRatio }" title="双击查看大图" @dblclick="emit('open')">
      <img
        v-if="image.thumbnailUrl && !broken"
        :src="image.thumbnailUrl"
        :alt="image.name || '私有图片'"
        loading="lazy"
        decoding="async"
        @error="broken = true"
      />
      <div v-else class="space-card-fallback">图片加载失败</div>

      <span v-if="selected" class="space-card-badge">
        <span class="space-card-badge-dot"></span>
        当前选中
      </span>

      <div class="space-card-actions">
        <Tooltip title="下载原图">
          <Button shape="circle" type="text" aria-label="下载原图" @click.stop="emit('download')">
            <template #icon><DownloadOutlined /></template>
          </Button>
        </Tooltip>
        <Tooltip title="删除图片">
          <Button shape="circle" type="text" danger aria-label="删除图片" @click.stop="emit('delete')">
            <template #icon><DeleteOutlined /></template>
          </Button>
        </Tooltip>
      </div>
    </div>

    <div class="space-card-body">
      <div class="space-card-title-row">
        <h2 class="space-card-title" :title="image.name || undefined">{{ image.name || '未命名图片' }}</h2>
        <span v-if="image.category" class="space-card-category">{{ image.category }}</span>
      </div>
      <div v-if="visibleTags.length" class="space-card-tags">
        <span v-for="tag in visibleTags" :key="tag" class="space-card-tag">#{{ tag }}</span>
      </div>
      <div class="space-card-meta">
        <span>{{ formatRelativeTime(image.createTime) }}</span>
        <span class="space-card-size">{{ formatSize(image.picSize) }}</span>
      </div>
    </div>
  </article>
</template>

<style scoped>
.space-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-surface);
  cursor: pointer;
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}

.space-card:hover {
  box-shadow: var(--cp-shadow-card);
  transform: translateY(-2px);
}

.space-card-selected {
  border-color: var(--cp-accent);
  box-shadow: 0 0 0 2px var(--cp-accent);
}

.space-card-media {
  position: relative;
  overflow: hidden;
  background: var(--cp-bg-soft);
}

.space-card-media img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.space-card-fallback {
  display: flex;
  height: 100%;
  align-items: center;
  justify-content: center;
  color: var(--cp-text-muted);
  font-size: 12px;
}

.space-card-badge {
  position: absolute;
  top: 10px;
  left: 10px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 9px;
  border-radius: 999px;
  background: rgba(17, 24, 39, 0.85);
  backdrop-filter: blur(6px);
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.space-card-badge-dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: #34d399;
}

.space-card-actions {
  position: absolute;
  right: 10px;
  bottom: 10px;
  display: flex;
  gap: 4px;
  padding: 4px;
  border-radius: var(--cp-radius);
  background: rgba(17, 24, 39, 0.78);
  backdrop-filter: blur(6px);
  opacity: 0;
  transition: opacity 0.2s ease;
}

.space-card-actions :deep(.ant-btn) {
  color: #fff;
}

.space-card-actions :deep(.ant-btn:hover) {
  background: rgba(255, 255, 255, 0.18);
}

.space-card-actions :deep(.ant-btn.ant-btn-dangerous) {
  color: #fda4af;
}

.space-card-actions :deep(.ant-btn.ant-btn-dangerous:hover) {
  background: rgba(244, 63, 94, 0.8);
  color: #fff;
}

.space-card-media:hover .space-card-actions,
.space-card-media:focus-within .space-card-actions {
  opacity: 1;
}

/* 触屏没有 hover，操作入口常驻 */
@media (hover: none) {
  .space-card-actions {
    opacity: 1;
  }
}

.space-card-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 14px 14px;
}

.space-card-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.space-card-title {
  overflow: hidden;
  margin: 0;
  font-family: 'Plus Jakarta Sans', Inter, sans-serif;
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.space-card-category {
  flex: none;
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--cp-bg);
  color: var(--cp-text-soft);
  font-size: 11px;
  font-weight: 500;
}

.space-card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.space-card-tag {
  padding: 1px 6px;
  border: 1px solid var(--cp-border-subtle);
  border-radius: 4px;
  background: var(--cp-bg-soft);
  color: var(--cp-text-soft);
  font-size: 11px;
}

.space-card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 8px;
  border-top: 1px solid var(--cp-border-subtle);
  color: var(--cp-text-muted);
  font-size: 11px;
}

.space-card-size {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
</style>