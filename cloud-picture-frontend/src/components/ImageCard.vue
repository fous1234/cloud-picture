<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { Avatar, Button, Tag, Tooltip } from 'ant-design-vue'
import { DeleteOutlined, EditOutlined, SafetyCertificateOutlined } from '@ant-design/icons-vue'
import type { ImageVO } from '../api/types'
import { openEdit, openReview, REVIEW_STATUS_COLOR, REVIEW_STATUS_TEXT } from '../stores/ui'

const props = defineProps<{
  image: ImageVO
  showStatus?: boolean
  canEdit?: boolean
  canDelete?: boolean
  canReview?: boolean
}>()

const emit = defineEmits<{ delete: [ImageVO] }>()

const broken = ref(false)

// 用后端返回的宽高占位，避免图片加载完成前的布局跳动
const aspectRatio = computed(() => {
  const { picWidth, picHeight } = props.image
  return picWidth && picHeight ? `${picWidth} / ${picHeight}` : '4 / 3'
})

const visibleTags = computed(() => (props.image.tags ?? []).slice(0, 3))
const hasActions = computed(() => !!(props.canEdit || props.canDelete || props.canReview))
</script>

<template>
  <article class="card" :class="{ 'card-with-status': showStatus }">
    <RouterLink :to="`/image/${image.id}`" class="card-media" :style="{ aspectRatio }">
      <img
        v-if="image.thumbnailUrl && !broken"
        :src="image.thumbnailUrl"
        :alt="image.name || '图片预览'"
        loading="lazy"
        decoding="async"
        @error="broken = true"
      />
      <div v-else class="card-media-fallback">图片加载失败</div>

      <div v-if="hasActions" class="card-overlay">
        <Tooltip v-if="canEdit" title="编辑信息">
          <Button
            shape="circle"
            type="text"
            aria-label="编辑信息"
            @click.prevent.stop="openEdit(image)"
          >
            <template #icon><EditOutlined /></template>
          </Button>
        </Tooltip>
        <Tooltip v-if="canReview" title="审核">
          <Button
            shape="circle"
            type="text"
            aria-label="审核图片"
            @click.prevent.stop="openReview(image)"
          >
            <template #icon><SafetyCertificateOutlined /></template>
          </Button>
        </Tooltip>
        <Tooltip v-if="canDelete" title="删除">
          <Button
            shape="circle"
            type="text"
            danger
            aria-label="删除图片"
            @click.prevent.stop="emit('delete', image)"
          >
            <template #icon><DeleteOutlined /></template>
          </Button>
        </Tooltip>
      </div>

      <Tag
        v-if="showStatus"
        class="card-status"
        :color="REVIEW_STATUS_COLOR[image.reviewStatus]"
      >
        {{ REVIEW_STATUS_TEXT[image.reviewStatus] }}
      </Tag>
    </RouterLink>

    <div class="card-body">
      <RouterLink :to="`/image/${image.id}`" class="card-title">
        {{ image.name || '未命名图片' }}
      </RouterLink>

      <div class="card-meta">
        <span v-if="image.owner" class="card-owner">
          <Avatar :src="image.owner.avatar || undefined" :size="20">
            {{ (image.owner.name || 'U').slice(0, 1) }}
          </Avatar>
          <span class="card-owner-name">{{ image.owner.name || '未知用户' }}</span>
        </span>
        <span v-if="image.category" class="card-category">{{ image.category }}</span>
      </div>

      <div v-if="visibleTags.length" class="card-tags">
        <Tag v-for="tag in visibleTags" :key="tag">{{ tag }}</Tag>
      </div>

      <p v-if="image.source === 'PEXELS'" class="pexels-credit">
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

      <p v-if="showStatus && image.reviewStatus === 2 && image.reviewMessage" class="card-reason">
        拒绝理由：{{ image.reviewMessage }}
      </p>
    </div>
  </article>
</template>

<style scoped>
.card {
  background: var(--cp-bg);
  border-radius: var(--cp-radius);
  overflow: hidden;
  border: 1px solid var(--cp-border);
  transition: box-shadow 0.2s ease, transform 0.2s ease;
}

.card:not(.card-with-status) {
  overflow: visible;
  border: 0;
  background: transparent;
}

.card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

.card-media {
  position: relative;
  display: block;
  background: var(--cp-bg-soft);
}

.card:not(.card-with-status) .card-media {
  overflow: hidden;
  border-radius: 10px;
}

.card-media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.card-media-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--cp-text-soft);
  font-size: 12px;
}

.card-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  gap: 8px;
  align-items: flex-start;
  justify-content: flex-end;
  padding: 8px;
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0.35), transparent 45%);
  opacity: 0;
  transition: opacity 0.2s ease;
}

.card-overlay :deep(.ant-btn) {
  background: rgba(255, 255, 255, 0.9);
  color: var(--cp-text);
}

.card-media:hover .card-overlay,
.card-media:focus-within .card-overlay {
  opacity: 1;
}

/* 触屏没有 hover，操作入口常驻 */
@media (hover: none) {
  .card-overlay {
    opacity: 1;
  }
}

.card-status {
  position: absolute;
  left: 8px;
  bottom: 8px;
  margin: 0;
}

.card-body {
  padding: 10px 12px 14px;
}

.card:not(.card-with-status) .card-body {
  padding: 9px 2px 12px;
}

.card-title {
  display: block;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-title:hover {
  text-decoration: underline;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  font-size: 12px;
  color: var(--cp-text-soft);
}

.card-owner {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.card-owner-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-category::before {
  content: '·';
  margin-right: 8px;
}

.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 8px;
}

.card-reason {
  margin: 8px 0 0;
  font-size: 12px;
  color: #cf1322;
}

.pexels-credit {
  margin: 7px 0 0;
  color: var(--cp-text-soft);
  font-size: 11px;
}

.pexels-credit a {
  text-decoration: underline;
  text-underline-offset: 2px;
}

@media (max-width: 575px) {
  .card:not(.card-with-status) {
    position: relative;
    border-radius: 10px;
  }

  .card:not(.card-with-status) .card-body {
    position: relative;
    z-index: 1;
    min-height: 62px;
    margin-top: -62px;
    padding: 24px 9px 8px;
    background: linear-gradient(transparent, rgba(10, 13, 16, 0.78));
    color: #fff;
    pointer-events: none;
  }

  .card:not(.card-with-status) .card-title {
    color: #fff;
    font-size: 11px;
    line-height: 1.35;
    white-space: normal;
  }

  .card:not(.card-with-status) .card-meta {
    margin-top: 4px;
    color: rgba(255, 255, 255, 0.82);
    font-size: 10px;
  }

  .card:not(.card-with-status) .card-category::before {
    margin-right: 3px;
  }

  .card:not(.card-with-status) .card-tags {
    display: none;
  }
}
</style>
