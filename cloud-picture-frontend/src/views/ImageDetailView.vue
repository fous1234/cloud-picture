<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Alert,
  Avatar,
  Button,
  Descriptions,
  DescriptionsItem,
  Result,
  Skeleton,
  Tag,
} from 'ant-design-vue'
import { ArrowLeftOutlined, DeleteOutlined, EditOutlined, SafetyCertificateOutlined } from '@ant-design/icons-vue'
import type { ImageVO } from '../api/types'
import { getImage } from '../api/image'
import { errorMessage } from '../api/http'
import { isAdmin, session } from '../stores/session'
import {
  REVIEW_STATUS_COLOR,
  REVIEW_STATUS_TEXT,
  dataVersion,
  openEdit,
  openReview,
  ownerCache,
} from '../stores/ui'
import { confirmDeleteImage, isOwner } from '../utils/imageActions'
import { formatDateTime, formatDimensions, formatSize } from '../utils/format'

const route = useRoute()
const router = useRouter()

const image = ref<ImageVO | null>(null)
const loading = ref(true)
const error = ref<string | null>(null)
const missing = ref(false)
const imageBroken = ref(false)
/** 签名地址失效只自动重取一次，再失败就交给用户手动重试 */
let refreshedOnce = false

const id = computed(() => String(route.params.id))

const owner = computed(() => {
  const current = image.value
  if (!current) return null
  if (current.owner) return current.owner
  return current.ownerId ? ownerCache.get(current.ownerId) ?? null : null
})

const canEdit = computed(() => !!image.value && isOwner(image.value))
const canReview = computed(() => !!image.value && isAdmin() && image.value.reviewStatus !== 1)
const canDelete = computed(() => !!image.value && (isOwner(image.value) || isAdmin()))

async function load() {
  loading.value = true
  error.value = null
  missing.value = false
  imageBroken.value = false
  try {
    image.value = await getImage(id.value)
  } catch (e) {
    image.value = null
    // 无权访问与不存在在后端都是 404，统一展示为“图片不存在或无权访问”
    if (typeof e === 'object' && e !== null && 'status' in e && (e as { status: number }).status === 404) {
      missing.value = true
    } else {
      error.value = errorMessage(e)
    }
  } finally {
    loading.value = false
  }
}

async function onImageError() {
  if (refreshedOnce) {
    imageBroken.value = true
    return
  }
  refreshedOnce = true
  await load()
}

function onDelete() {
  if (!image.value) return
  confirmDeleteImage(image.value, () => router.push({ name: 'gallery' }))
}

watch(id, () => {
  refreshedOnce = false
  load()
}, { immediate: true })
watch(dataVersion, load)
</script>

<template>
  <div class="cp-container cp-page">
    <Button class="back-link" type="link" @click="router.push({ name: 'gallery' })">
      <template #icon><ArrowLeftOutlined /></template>
      返回图库
    </Button>

    <div v-if="loading" class="detail">
      <Skeleton.Image active class="detail-skeleton-image" />
      <Skeleton :paragraph="{ rows: 6 }" active />
    </div>

    <Result
      v-else-if="missing"
      status="404"
      title="图片不存在或无权访问"
      sub-title="图片可能已被删除，或尚未通过审核。"
    >
      <template #extra>
        <Button type="primary" @click="router.push({ name: 'gallery' })">返回图库</Button>
      </template>
    </Result>

    <Alert
      v-else-if="error"
      type="error"
      show-icon
      message="详情加载失败"
      :description="error"
    >
      <template #action>
        <Button size="small" @click="load">重试</Button>
      </template>
    </Alert>

    <template v-else-if="image">
      <Alert
        v-if="image.reviewStatus !== 1 && (canEdit || canReview)"
        class="detail-status"
        :type="image.reviewStatus === 0 ? 'warning' : 'error'"
        show-icon
        :message="
          image.reviewStatus === 0
            ? '这张图片还在等待管理员审核，仅你和管理员可见'
            : '这张图片未通过审核'
        "
        :description="image.reviewStatus === 2 ? `拒绝理由：${image.reviewMessage || '未填写'}` : undefined"
      />

      <div class="detail">
        <div class="detail-media">
          <img
            v-if="image.url && !imageBroken"
            :src="image.url"
            :alt="image.name || '图片'"
            @error="onImageError"
          />
          <div v-else class="detail-media-fallback">
            <p>图片加载失败，签名地址可能已过期。</p>
            <Button @click="load">重新加载</Button>
          </div>
          <div v-if="image" class="detail-image-meta">
            <span>RAW MASTER · {{ formatDimensions(image.picWidth, image.picHeight) }}</span>
            <span>{{ image.picFormat || 'IMAGE' }}</span>
          </div>
        </div>

        <div class="detail-info">
          <p class="section-kicker">PHOTO ARCHIVE</p>
          <div class="detail-heading">
            <h1 class="cp-page-title">{{ image.name || '未命名图片' }}</h1>
            <Tag :color="REVIEW_STATUS_COLOR[image.reviewStatus]">
              {{ REVIEW_STATUS_TEXT[image.reviewStatus] }}
            </Tag>
          </div>

          <p class="detail-introduction">
            {{ image.introduction || '暂无简介' }}
          </p>

          <div v-if="image.source === 'PEXELS'" class="pexels-attribution">
            <span>图片来源：Pexels</span>
            <span v-if="image.photographer">· 摄影师：
              <a
                v-if="image.photographerUrl"
                :href="image.photographerUrl"
                target="_blank"
                rel="noopener noreferrer"
              >{{ image.photographer }}</a>
              <span v-else>{{ image.photographer }}</span>
            </span>
            <a
              v-if="image.sourcePageUrl"
              :href="image.sourcePageUrl"
              target="_blank"
              rel="noopener noreferrer"
            >查看 Pexels 来源</a>
          </div>

          <div v-if="image.tags?.length" class="detail-tags">
            <Tag v-for="tag in image.tags" :key="tag">{{ tag }}</Tag>
          </div>

          <Descriptions :column="1" size="small" class="detail-descriptions">
            <DescriptionsItem label="分类">{{ image.category || '—' }}</DescriptionsItem>
            <DescriptionsItem label="尺寸">
              {{ formatDimensions(image.picWidth, image.picHeight) }}
            </DescriptionsItem>
            <DescriptionsItem label="格式">{{ image.picFormat || '—' }}</DescriptionsItem>
            <DescriptionsItem label="大小">{{ formatSize(image.picSize) }}</DescriptionsItem>
            <DescriptionsItem label="上传时间">{{ formatDateTime(image.createTime) }}</DescriptionsItem>
            <DescriptionsItem label="上传者">
              <span v-if="owner" class="detail-owner">
                <Avatar :src="owner.avatar || undefined" :size="20">
                  {{ (owner.name || 'U').slice(0, 1) }}
                </Avatar>
                {{ owner.name || '未知用户' }}
              </span>
              <span v-else>—</span>
            </DescriptionsItem>
            <DescriptionsItem v-if="image.reviewStatus !== 1 && (canEdit || isAdmin())" label="审核信息">
              {{ image.reviewMessage || '—' }}
            </DescriptionsItem>
          </Descriptions>

          <div class="detail-actions">
            <Button v-if="canEdit" @click="openEdit(image)">
              <template #icon><EditOutlined /></template>
              编辑信息
            </Button>
            <Button v-if="canReview" @click="openReview(image)">
              <template #icon><SafetyCertificateOutlined /></template>
              审核
            </Button>
            <Button v-if="canDelete" danger @click="onDelete">
              <template #icon><DeleteOutlined /></template>
              删除
            </Button>
          </div>

          <p v-if="session.user && !canEdit && !isAdmin()" class="detail-hint">
            只有图片所有者可以修改或删除这张图片。
          </p>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.back-link {
  padding-left: 0;
  margin-bottom: 12px;
}

.detail {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(320px, 0.9fr);
  gap: 24px;
  align-items: start;
}

.detail-skeleton-image {
  width: 100%;
  height: 360px;
}

.detail-media {
  position: relative;
  background: #202326;
  border-radius: 10px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 480px;
}

.detail-media img {
  width: 100%;
  max-height: 70vh;
  object-fit: contain;
  display: block;
}

.detail-media-fallback {
  padding: 48px 16px;
  text-align: center;
  color: #fff;
}

.detail-image-meta {
  position: absolute;
  right: 14px;
  bottom: 12px;
  left: 14px;
  display: flex;
  justify-content: space-between;
  color: #fff;
  font-size: 11px;
  text-shadow: 0 1px 5px #000;
}

.section-kicker {
  margin: 0 0 8px;
  color: var(--cp-text-soft);
  font-size: 10px;
  letter-spacing: 0.14em;
}

.detail-info {
  padding: 20px;
  border: 1px solid var(--cp-border);
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 8px 28px rgba(20, 27, 36, 0.04);
}

.detail-info :deep(.ant-descriptions-item-label) {
  color: var(--cp-text-soft);
  font-size: 12px;
}

.detail-info :deep(.ant-descriptions-item-content) {
  color: var(--cp-text);
  font-size: 12px;
}

.detail-heading {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.detail-introduction {
  color: var(--cp-text-soft);
  margin: 8px 0 12px;
}

.pexels-attribution {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0 0 14px;
  color: var(--cp-text-soft);
  font-size: 12px;
}

.pexels-attribution a {
  text-decoration: underline;
  text-underline-offset: 2px;
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 16px;
}

.detail-descriptions {
  margin-bottom: 20px;
}

.detail-owner {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.detail-status {
  margin-bottom: 20px;
}

.detail-hint {
  margin-top: 16px;
  color: var(--cp-text-soft);
  font-size: 13px;
}

@media (max-width: 899px) {
  .detail {
    grid-template-columns: 1fr;
    gap: 20px;
  }

  .detail-media {
    min-height: 320px;
  }
}

@media (max-width: 575px) {
  .detail-media {
    min-height: 240px;
  }

  .detail-info {
    padding: 16px;
  }
}
</style>
