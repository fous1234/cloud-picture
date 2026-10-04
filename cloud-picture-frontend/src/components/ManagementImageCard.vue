<script setup lang="ts">
import { computed, ref } from 'vue'
import { Avatar, Button, Tag } from 'ant-design-vue'
import { CheckOutlined, CloseOutlined, DeleteOutlined, EditOutlined } from '@ant-design/icons-vue'
import { RouterLink } from 'vue-router'
import type { ImageVO } from '../api/types'
import { openEdit, openReview, REVIEW_STATUS_COLOR, REVIEW_STATUS_TEXT } from '../stores/ui'
import { formatDateTime, formatSize } from '../utils/format'

const props = withDefaults(
  defineProps<{
    image: ImageVO
    mode: 'review' | 'mine'
    batchMode?: boolean
    selected?: boolean
  }>(),
  { batchMode: false, selected: false },
)

const emit = defineEmits<{
  delete: [ImageVO]
  toggleSelect: [ImageVO]
}>()

const broken = ref(false)

const aspectRatio = computed(() => {
  const { picWidth, picHeight } = props.image
  return picWidth && picHeight ? `${picWidth} / ${picHeight}` : '16 / 10'
})

const visibleTags = computed(() => (props.image.tags ?? []).slice(0, 3))
const imageUrl = computed(() => props.image.thumbnailUrl || props.image.url || '')
</script>

<template>
  <article class="management-card" :class="{ 'management-card-selected': selected }">
    <RouterLink
      :to="`/image/${image.id}`"
      class="management-card-media"
      :style="{ aspectRatio }"
    >
      <img
        v-if="imageUrl && !broken"
        :src="imageUrl"
        :alt="image.name || '图片预览'"
        loading="lazy"
        decoding="async"
        @error="broken = true"
      />
      <span v-else class="management-card-fallback">图片加载失败</span>

      <span v-if="mode === 'review' && batchMode" class="selection-control">
        <input
          type="checkbox"
          :checked="selected"
          :aria-label="`选择${image.name || '图片'}`"
          @click.stop
          @change.stop="emit('toggleSelect', image)"
        />
      </span>
      <Tag
        class="management-card-status"
        :color="REVIEW_STATUS_COLOR[image.reviewStatus]"
      >
        {{ REVIEW_STATUS_TEXT[image.reviewStatus] }}
      </Tag>
      <span class="management-card-spec">
        {{ formatSize(image.picSize) }} · {{ (image.picFormat || 'IMAGE').toUpperCase() }}
      </span>
    </RouterLink>

    <div class="management-card-body">
      <div class="management-card-heading">
        <RouterLink :to="`/image/${image.id}`" class="management-card-title">
          {{ image.name || '未命名图片' }}
        </RouterLink>
        <span class="management-card-time">{{ formatDateTime(image.createTime) }}</span>
      </div>

      <div v-if="mode === 'review'" class="management-card-owner">
        <Avatar :src="image.owner?.avatar || undefined" :size="20">
          {{ (image.owner?.name || 'U').slice(0, 1) }}
        </Avatar>
        <span>{{ image.owner?.name || '未知用户' }}</span>
      </div>

      <div class="management-card-meta">
        <span v-if="image.category">{{ image.category }}</span>
        <span v-if="image.picWidth && image.picHeight">
          {{ image.picWidth }} × {{ image.picHeight }}
        </span>
      </div>

      <div v-if="visibleTags.length" class="management-card-tags">
        <Tag v-for="tag in visibleTags" :key="tag">#{{ tag }}</Tag>
      </div>

      <p v-if="image.source === 'PEXELS'" class="management-card-credit">
        <template v-if="image.photographer">
          Photo by
          <a
            v-if="image.photographerUrl"
            :href="image.photographerUrl"
            target="_blank"
            rel="noopener noreferrer"
          >{{ image.photographer }}</a>
          <span v-else>{{ image.photographer }}</span>
          on Pexels
        </template>
        <span v-else>图片来源：Pexels</span>
      </p>

      <p v-if="mode === 'mine' && image.reviewStatus === 2 && image.reviewMessage" class="management-card-reason">
        {{ image.reviewMessage }}
      </p>

      <div class="management-card-actions">
        <template v-if="mode === 'review'">
          <Button
            v-if="image.reviewStatus === 0"
            size="small"
            class="management-action-approved"
            @click="openReview(image, 1)"
          >
            <template #icon><CheckOutlined /></template>
            通过
          </Button>
          <Button
            v-if="image.reviewStatus !== 2"
            size="small"
            danger
            @click="openReview(image, 2)"
          >
            <template #icon><CloseOutlined /></template>
            拒绝
          </Button>
          <Button
            v-else
            size="small"
            class="management-action-approved"
            @click="openReview(image, 1)"
          >再次审核</Button>
        </template>
        <template v-else>
          <Button size="small" type="text" @click="openEdit(image)">
            <template #icon><EditOutlined /></template>
            编辑信息
          </Button>
          <RouterLink :to="`/image/${image.id}`" class="management-detail-link">查看详情</RouterLink>
        </template>
        <Button
          size="small"
          type="text"
          danger
          class="management-delete-button"
          aria-label="删除图片"
          @click="emit('delete', image)"
        >
          <template #icon><DeleteOutlined /></template>
          <span class="management-delete-label">删除</span>
        </Button>
      </div>
    </div>
  </article>
</template>

<style scoped>
.management-card {
  display: flex;
  min-width: 0;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--cp-border-subtle);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-surface);
  box-shadow: 0 1px 3px rgba(17, 24, 39, 0.03);
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.management-card:hover,
.management-card-selected {
  border-color: var(--cp-border);
  box-shadow: 0 8px 24px rgba(17, 24, 39, 0.07);
}

.management-card-media {
  position: relative;
  display: block;
  overflow: hidden;
  background: var(--cp-bg-soft);
}

.management-card-media img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.35s ease;
}

.management-card:hover .management-card-media img {
  transform: scale(1.03);
}

.management-card-fallback {
  display: grid;
  width: 100%;
  height: 100%;
  place-items: center;
  color: var(--cp-text-soft);
  font-size: 12px;
}

.management-card-status {
  position: absolute;
  top: 10px;
  left: 10px;
  margin: 0;
  padding: 3px 9px;
  border: 1px solid transparent;
  font-size: 11px;
  font-weight: 600;
}

.management-card-spec {
  position: absolute;
  right: 10px;
  bottom: 10px;
  padding: 3px 7px;
  border-radius: 4px;
  background: rgba(17, 24, 39, 0.72);
  color: #fff;
  font-size: 10px;
  letter-spacing: 0.04em;
}

.selection-control {
  position: absolute;
  top: 10px;
  right: 10px;
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
}

.selection-control input {
  width: 16px;
  height: 16px;
  accent-color: var(--cp-accent);
}

.management-card-body {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px 16px;
}

.management-card-heading,
.management-card-owner,
.management-card-actions {
  display: flex;
  align-items: center;
}

.management-card-heading {
  min-width: 0;
  justify-content: space-between;
  gap: 8px;
}

.management-card-title {
  overflow: hidden;
  color: var(--cp-text);
  font-family: 'Plus Jakarta Sans', Inter, sans-serif;
  font-size: 16px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.management-card-title:hover,
.management-detail-link:hover {
  color: var(--cp-text-soft);
}

.management-card-time {
  flex: none;
  color: var(--cp-text-muted);
  font-size: 11px;
}

.management-card-owner,
.management-card-meta {
  gap: 6px;
  color: var(--cp-text-soft);
  font-size: 12px;
}

.management-card-meta {
  display: flex;
  flex-wrap: wrap;
}

.management-card-owner :deep(.ant-avatar) {
  flex: none;
}

.management-card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.management-card-tags :deep(.ant-tag) {
  margin: 0;
  border: 0;
  background: var(--cp-bg-soft);
  color: var(--cp-text-soft);
  font-size: 11px;
}

.management-card-credit,
.management-card-reason {
  margin: 0;
  color: var(--cp-text-soft);
  font-size: 11px;
  line-height: 1.5;
}

.management-card-credit a {
  text-decoration: underline;
  text-underline-offset: 2px;
}

.management-card-reason {
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--cp-status-rejected-bg);
  color: var(--cp-status-rejected-fg);
}

.management-card-actions {
  flex-wrap: wrap;
  gap: 6px;
  margin: auto -16px -16px;
  padding: 8px 10px;
  background: var(--cp-bg-soft);
}

.management-card-actions :deep(.ant-btn) {
  border-radius: 8px;
  font-size: 12px;
}

.management-action-approved {
  border-color: var(--cp-status-approved-border);
  background: var(--cp-status-approved-bg);
  color: var(--cp-status-approved-fg);
}

.management-detail-link {
  padding: 4px 8px;
  color: var(--cp-text);
  font-size: 12px;
}

.management-delete-button {
  margin-left: auto;
}

.management-delete-label {
  display: none;
}

@media (max-width: 575px) {
  .management-card-body {
    padding: 12px;
  }

  .management-card-actions {
    margin: auto -12px -12px;
  }

  .management-delete-label {
    display: inline;
  }
}
</style>
