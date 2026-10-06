<script setup lang="ts">
import { computed } from 'vue'
import { Button } from 'ant-design-vue'
import { DeleteOutlined, DownloadOutlined, LockOutlined, PictureOutlined } from '@ant-design/icons-vue'
import type { SpaceImageVO } from '../api/types'
import { formatDateTime, formatDimensions, formatSize } from '../utils/format'

const props = defineProps<{ image: SpaceImageVO | null }>()
const emit = defineEmits<{ download: []; delete: []; open: [] }>()

const tags = computed(() => props.image?.tags ?? [])
</script>

<template>
  <aside class="space-detail">
    <template v-if="image">
      <!-- 预览画布：深色底便于看原片，用项目主色，不引入新颜色 -->
      <div class="space-detail-canvas">
        <img
          v-if="image.url"
          class="space-detail-image"
          :src="image.url"
          :alt="image.name || '私有图片预览'"
          title="点击查看大图"
          decoding="async"
          @click="emit('open')"
        />
        <div v-else class="space-detail-canvas-fallback">图片加载失败</div>
        <span class="space-detail-resolution">
          <PictureOutlined />
          {{ formatDimensions(image.picWidth, image.picHeight) }} · {{ formatSize(image.picSize) }}
        </span>
      </div>

      <div class="space-detail-body">
        <div class="space-detail-badges">
          <span class="space-detail-pill">
            <LockOutlined />
            仅自己可见 · 免审核
          </span>
        </div>

        <h3 class="space-detail-title">{{ image.name || '未命名图片' }}</h3>

        <p v-if="image.introduction" class="space-detail-intro">{{ image.introduction }}</p>

        <div class="space-detail-attrs">
          <div class="space-detail-attr">
            <span class="space-detail-attr-label">所属分类</span>
            <span v-if="image.category" class="space-detail-attr-value">{{ image.category }}</span>
            <span v-else class="space-detail-attr-empty">—</span>
          </div>
          <div class="space-detail-attr">
            <span class="space-detail-attr-label">标签属性</span>
            <div v-if="tags.length" class="space-detail-tags">
              <span v-for="tag in tags" :key="tag" class="space-detail-tag">#{{ tag }}</span>
            </div>
            <span v-else class="space-detail-attr-empty">—</span>
          </div>
        </div>

        <div class="space-detail-specs">
          <div class="space-detail-spec">
            <span class="space-detail-spec-label">分辨率</span>
            <span class="space-detail-spec-value">{{ formatDimensions(image.picWidth, image.picHeight) }}</span>
          </div>
          <div class="space-detail-spec">
            <span class="space-detail-spec-label">文件大小</span>
            <span class="space-detail-spec-value">{{ formatSize(image.picSize) }}</span>
          </div>
          <div class="space-detail-spec">
            <span class="space-detail-spec-label">图片格式</span>
            <span class="space-detail-spec-value">{{ image.picFormat || '—' }}</span>
          </div>
          <div class="space-detail-spec">
            <span class="space-detail-spec-label">上传时间</span>
            <span class="space-detail-spec-value">{{ formatDateTime(image.createTime) }}</span>
          </div>
        </div>

        <div class="space-detail-actions">
          <Button type="primary" class="space-detail-download" @click="emit('download')">
            <template #icon><DownloadOutlined /></template>
            下载原图
          </Button>
          <Button danger @click="emit('delete')">
            <template #icon><DeleteOutlined /></template>
            删除图片
          </Button>
        </div>
      </div>
    </template>

    <div v-else class="space-detail-empty">选择一张图片查看详情</div>
  </aside>
</template>

<style scoped>
.space-detail {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-surface);
  box-shadow: var(--cp-shadow-card);
}

.space-detail-canvas {
  position: relative;
  display: flex;
  aspect-ratio: 4 / 3;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: var(--cp-accent);
}

.space-detail-canvas img {
  width: 100%;
  height: 100%;
  padding: 8px;
  object-fit: contain;
}

.space-detail-image {
  padding: 8px;
  cursor: zoom-in;
}

.space-detail-canvas-fallback {
  color: rgba(255, 255, 255, 0.7);
  font-size: 12px;
}

.space-detail-resolution {
  position: absolute;
  bottom: 12px;
  left: 12px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: var(--cp-radius);
  background: rgba(0, 0, 0, 0.75);
  backdrop-filter: blur(6px);
  color: #fff;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
}

.space-detail-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 20px;
}

.space-detail-badges {
  display: flex;
  justify-content: flex-end;
}

.space-detail-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 10px;
  border: 1px solid var(--cp-status-approved-border);
  border-radius: 999px;
  background: var(--cp-status-approved-bg);
  color: var(--cp-status-approved-fg);
  font-size: 11px;
  font-weight: 600;
}

.space-detail-title {
  margin: 0;
  font-family: 'Plus Jakarta Sans', Inter, sans-serif;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.4;
  letter-spacing: -0.01em;
}

.space-detail-intro {
  margin: 0;
  padding: 12px;
  border: 1px solid var(--cp-border-subtle);
  border-radius: var(--cp-radius);
  background: var(--cp-bg-soft);
  color: var(--cp-text-soft);
  font-size: 12px;
  line-height: 1.7;
}

.space-detail-attrs {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.space-detail-attr {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  font-size: 12px;
}

.space-detail-attr-label {
  flex: none;
  width: 56px;
  color: var(--cp-text-muted);
  font-weight: 500;
}

.space-detail-attr-value {
  padding: 2px 10px;
  border-radius: 6px;
  background: var(--cp-bg);
  color: var(--cp-text);
  font-weight: 500;
}

.space-detail-attr-empty {
  color: var(--cp-text-muted);
}

.space-detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.space-detail-tag {
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--cp-bg);
  color: var(--cp-text-soft);
  font-weight: 500;
}

.space-detail-specs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--cp-border-subtle);
  border-radius: var(--cp-radius);
  background: var(--cp-bg-soft);
}

.space-detail-spec {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.space-detail-spec-label {
  color: var(--cp-text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 10px;
  letter-spacing: 0.08em;
}

.space-detail-spec-value {
  color: var(--cp-text);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  font-weight: 600;
}

.space-detail-actions {
  display: flex;
  gap: 8px;
}

.space-detail-download {
  flex: 1;
}

.space-detail-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 64px 24px;
  color: var(--cp-text-muted);
  font-size: 12px;
}
</style>