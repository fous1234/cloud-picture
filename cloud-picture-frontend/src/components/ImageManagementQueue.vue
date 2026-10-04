<script setup lang="ts">
import { Button, Pagination, Skeleton } from 'ant-design-vue'
import { VerifiedOutlined } from '@ant-design/icons-vue'
import type { Id, ImageVO } from '../api/types'
import EmptyState from './EmptyState.vue'
import ErrorState from './ErrorState.vue'
import ManagementQueueItem from './ManagementQueueItem.vue'

defineProps<{
  images: ImageVO[]
  mode: 'review' | 'mine'
  selectedId: Id | null
  loading: boolean
  error: string | null
  total: number
  current: number
  size: number
  countLabel: string
  emptyDescription: string
  showTips?: boolean
  showUploadAction?: boolean
}>()

const emit = defineEmits<{
  select: [Id]
  retry: []
  page: [page: number, size: number]
  upload: []
}>()

function onPage(page: number, size: number) {
  emit('page', page, size)
}
</script>

<template>
  <section class="queue">
    <header class="queue-head">
      <div class="queue-head-title">
        <h2>{{ mode === 'review' ? '审核队列' : '我的上传' }}</h2>
        <span class="queue-count">{{ countLabel }}</span>
      </div>
      <div class="queue-sync">
        <span class="queue-sync-dot"></span>
        <span>实时同步中</span>
      </div>
    </header>

    <Skeleton v-if="loading && !images.length" :paragraph="{ rows: 5 }" active />

    <ErrorState
      v-else-if="error"
      :message="mode === 'review' ? '审核列表加载失败' : '我的上传加载失败'"
      :description="error"
      @retry="emit('retry')"
    />

    <EmptyState v-else-if="!images.length" :description="emptyDescription">
      <Button v-if="showUploadAction" type="primary" @click="emit('upload')">上传图片</Button>
    </EmptyState>

    <div v-else class="queue-list">
      <ManagementQueueItem
        v-for="image in images"
        :key="image.id"
        :image="image"
        :mode="mode"
        :selected="image.id === selectedId"
        @select="emit('select', $event)"
      />
    </div>

    <div v-if="loading && images.length" class="queue-refreshing">正在刷新列表…</div>

    <Pagination
      v-if="total > 0"
      class="queue-pagination"
      :current="current"
      :page-size="size"
      :total="total"
      show-less-items
      @change="onPage"
    />

    <div v-if="showTips" class="queue-tips">
      <div class="queue-tips-head">
        <VerifiedOutlined />
        <span>审核准则速览</span>
      </div>
      <ul class="queue-tips-list">
        <li><strong>规格准入：</strong>画质需达到 2K 以上，确保原片噪点与失焦处于可接受容差。</li>
        <li><strong>权益核验：</strong>必须具备拍摄者真实版权，若含特写人像需备齐肖像权授权。</li>
        <li><strong>审美保真：</strong>严禁过度锐化、过度饱和滤镜，真实传递校园环境风貌。</li>
      </ul>
    </div>
  </section>
</template>

<style scoped>
.queue {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.queue-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 4px;
}

.queue-head-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.queue-head-title h2 {
  margin: 0;
  color: var(--cp-text);
  font-size: 15px;
  font-weight: 700;
}

.queue-count {
  padding: 2px 10px;
  border: 1px solid var(--cp-status-pending-border);
  border-radius: 999px;
  background: var(--cp-status-pending-bg);
  color: var(--cp-status-pending-fg);
  font-size: 12px;
  font-weight: 600;
}

.queue-sync {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--cp-text-muted);
  font-size: 12px;
}

.queue-sync-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #10b981;
}

.queue-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.queue-refreshing {
  color: var(--cp-text-soft);
  font-size: 12px;
  text-align: center;
}

.queue-pagination {
  align-self: center;
  margin-top: 4px;
}

.queue-tips {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-surface);
  box-shadow: var(--cp-shadow-subtle);
}

.queue-tips-head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--cp-text);
  font-size: 12px;
  font-weight: 700;
}

.queue-tips-head :deep(.anticon) {
  color: var(--cp-status-pending-fg);
  font-size: 18px;
}

.queue-tips-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
  color: var(--cp-text-soft);
  font-size: 12px;
}

.queue-tips-list li {
  position: relative;
  padding-left: 14px;
  line-height: 1.6;
}

.queue-tips-list li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 7px;
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--cp-text-muted);
}

.queue-tips-list strong {
  color: var(--cp-text);
  font-weight: 600;
}

@media (max-width: 575px) {
  .queue-head {
    flex-wrap: wrap;
  }
}
</style>