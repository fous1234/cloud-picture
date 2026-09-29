<script setup lang="ts">
import { Skeleton } from 'ant-design-vue'
import type { ImageVO } from '../api/types'
import ErrorState from './ErrorState.vue'
import EmptyState from './EmptyState.vue'
import ImageCard from './ImageCard.vue'

withDefaults(
  defineProps<{
    loading: boolean
    error?: string | null
    images: ImageVO[]
    emptyText: string
    skeletonCount?: number
    showStatus?: boolean
    canEdit?: (image: ImageVO) => boolean
    canDelete?: (image: ImageVO) => boolean
    canReview?: (image: ImageVO) => boolean
  }>(),
  { error: null, skeletonCount: 8 },
)

const emit = defineEmits<{ retry: []; delete: [ImageVO] }>()
</script>

<template>
  <div>
    <div v-if="loading" class="cp-masonry">
      <div v-for="index in skeletonCount" :key="index" class="skeleton-card">
        <Skeleton.Image active class="skeleton-image" />
        <Skeleton :paragraph="{ rows: 2 }" active class="skeleton-body" />
      </div>
    </div>

    <ErrorState v-else-if="error" message="图片加载失败" :description="error" @retry="emit('retry')" />

    <EmptyState v-else-if="!images.length" :description="emptyText">
      <slot name="empty-action" />
    </EmptyState>

    <div v-else class="cp-masonry">
      <ImageCard
        v-for="image in images"
        :key="image.id"
        :image="image"
        :show-status="showStatus"
        :can-edit="canEdit?.(image)"
        :can-delete="canDelete?.(image)"
        :can-review="canReview?.(image)"
        @delete="emit('delete', $event)"
      />
    </div>
  </div>
</template>

<style scoped>
.skeleton-card {
  break-inside: avoid;
  margin-bottom: 16px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius);
  overflow: hidden;
}

.skeleton-image {
  width: 100%;
  height: 180px;
}

.skeleton-image :deep(.ant-skeleton-image) {
  width: 100%;
  height: 100%;
}

.skeleton-body {
  padding: 12px;
}
</style>