<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { Skeleton } from 'ant-design-vue'
import type { ImageVO } from '../api/types'
import ErrorState from './ErrorState.vue'
import EmptyState from './EmptyState.vue'
import ImageCard from './ImageCard.vue'

const props = withDefaults(
  defineProps<{
    loading: boolean
    error?: string | null
    images: ImageVO[]
    emptyText: string
    skeletonCount?: number
    imageOnly?: boolean
    showStatus?: boolean
    canEdit?: (image: ImageVO) => boolean
    canDelete?: (image: ImageVO) => boolean
    canReview?: (image: ImageVO) => boolean
  }>(),
  { error: null, skeletonCount: 8, imageOnly: false },
)

const emit = defineEmits<{ retry: []; delete: [ImageVO] }>()
const columnCount = ref(4)

function updateColumnCount() {
  const width = window.innerWidth
  columnCount.value = width <= 340 ? 1 : width <= 899 ? 2 : width <= 1199 ? 3 : 4
}

function splitIntoColumns<T>(items: T[]) {
  const columns: T[][] = Array.from({ length: columnCount.value }, () => [])
  items.forEach((item, index) => columns[index % columnCount.value].push(item))
  return columns
}

const imageColumns = computed(() => splitIntoColumns(props.images))
const skeletonColumns = computed(() =>
  splitIntoColumns(Array.from({ length: props.skeletonCount }, (_, index) => index)),
)

onMounted(() => {
  updateColumnCount()
  window.addEventListener('resize', updateColumnCount)
})

onUnmounted(() => window.removeEventListener('resize', updateColumnCount))
</script>

<template>
  <div>
    <div v-if="props.loading" class="cp-masonry">
      <div v-for="(column, columnIndex) in skeletonColumns" :key="'skeleton-column-' + columnIndex" class="cp-masonry-column">
        <div
          v-for="index in column"
          :key="index"
          class="skeleton-card"
          :class="{ 'skeleton-card-image-only': props.imageOnly }"
        >
          <Skeleton.Image active class="skeleton-image" />
          <Skeleton v-if="!props.imageOnly" :paragraph="{ rows: 2 }" active class="skeleton-body" />
        </div>
      </div>
    </div>

    <ErrorState v-else-if="props.error" message="图片加载失败" :description="props.error" @retry="emit('retry')" />

    <EmptyState v-else-if="!props.images.length" :description="props.emptyText">
      <slot name="empty-action" />
    </EmptyState>

    <div v-else class="cp-masonry">
      <div v-for="(column, columnIndex) in imageColumns" :key="'image-column-' + columnIndex" class="cp-masonry-column">
        <ImageCard
          v-for="image in column"
          :key="image.id"
          :image="image"
          :image-only="props.imageOnly"
          :show-status="props.showStatus"
          :can-edit="props.canEdit?.(image)"
          :can-delete="props.canDelete?.(image)"
          :can-review="props.canReview?.(image)"
          @delete="emit('delete', $event)"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.skeleton-card {
  margin: 0;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius);
  overflow: hidden;
}

.skeleton-card-image-only {
  border: 0;
  border-radius: var(--cp-radius-lg);
}

.skeleton-image {
  width: 100%;
  height: 180px;
}

.skeleton-image :deep(.ant-skeleton-image) {
  width: 100%;
  height: 100%;
}

.skeleton-card-image-only .skeleton-image {
  height: auto;
  aspect-ratio: 4 / 3;
}

.skeleton-card-image-only .skeleton-image :deep(.ant-skeleton-image) {
  aspect-ratio: 4 / 3;
}

.skeleton-body {
  padding: 12px;
}
</style>
