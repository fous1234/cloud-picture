<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Alert,
  Button,
  Descriptions,
  DescriptionsItem,
  Result,
  Skeleton,
  Tag,
} from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  CloudDownloadOutlined,
  DownloadOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons-vue'
import { downloadImage, getSharedImage } from '../api/image'
import { errorMessage } from '../api/http'
import type { SharedImageVO } from '../api/types'
import { isLoggedIn } from '../stores/session'
import { formatDimensions, formatSize } from '../utils/format'

const route = useRoute()
const router = useRouter()

const image = ref<SharedImageVO | null>(null)
const loading = ref(true)
const invalid = ref(false)
const error = ref<string | null>(null)
const imageBroken = ref(false)
const downloadLoading = ref(false)
const downloadStarted = ref(false)
const downloadError = ref<string | null>(null)

const token = computed(() => String(route.params.token ?? ''))
const downloadLabel = computed(() => `原图下载（${formatSize(image.value?.picSize ?? null)}）`)
let loadRequestSequence = 0
let downloadRequestSequence = 0
let refreshedOnce = false

function resetDownloadState() {
  downloadRequestSequence++
  downloadLoading.value = false
  downloadStarted.value = false
  downloadError.value = null
}

async function load() {
  const currentToken = token.value
  const requestSequence = ++loadRequestSequence
  resetDownloadState()
  loading.value = true
  invalid.value = false
  error.value = null
  imageBroken.value = false
  try {
    const sharedImage = await getSharedImage(currentToken)
    if (requestSequence !== loadRequestSequence || token.value !== currentToken) return
    image.value = sharedImage
  } catch (e) {
    if (requestSequence !== loadRequestSequence || token.value !== currentToken) return
    image.value = null
    if (typeof e === 'object' && e !== null && 'status' in e && (e as { status: number }).status === 404) {
      invalid.value = true
    } else {
      error.value = errorMessage(e)
    }
  } finally {
    if (requestSequence === loadRequestSequence) loading.value = false
  }
}

function goToLogin() {
  router.push({ name: 'login', query: { redirect: route.fullPath } })
}

async function onDownload() {
  const currentImage = image.value
  if (!currentImage || downloadLoading.value) return
  if (!isLoggedIn()) {
    goToLogin()
    return
  }

  const requestSequence = ++downloadRequestSequence
  downloadLoading.value = true
  downloadStarted.value = false
  downloadError.value = null
  try {
    const url = await downloadImage(currentImage.id)
    if (requestSequence !== downloadRequestSequence || token.value !== String(route.params.token)) return

    const link = document.createElement('a')
    link.href = url
    link.download = ''
    link.style.display = 'none'
    document.body.appendChild(link)
    link.click()
    link.remove()
    downloadStarted.value = true
  } catch (e) {
    if (requestSequence === downloadRequestSequence) downloadError.value = errorMessage(e)
  } finally {
    if (requestSequence === downloadRequestSequence) downloadLoading.value = false
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

watch(token, () => {
  refreshedOnce = false
  load()
}, { immediate: true })
</script>

<template>
  <div class="cp-container cp-page shared-page">
    <div v-if="loading" class="shared-loading">
      <Skeleton.Image active class="shared-skeleton-image" />
      <Skeleton :paragraph="{ rows: 6 }" active />
    </div>

    <Result
      v-else-if="invalid"
      status="404"
      title="分享链接无效或已失效"
      sub-title="链接可能已被撤销，或图片当前无法公开访问。"
    >
      <template #extra>
        <Button type="primary" @click="router.push({ name: 'gallery' })">返回共享图库</Button>
      </template>
    </Result>

    <Alert
      v-else-if="error"
      type="error"
      show-icon
      message="分享图片加载失败"
      :description="error"
    >
      <template #action>
        <Button size="small" @click="load">重试</Button>
      </template>
    </Alert>

    <template v-else-if="image">
      <div class="shared-toolbar">
        <div class="shared-toolbar-leading">
          <Button class="shared-back-link" type="link" @click="router.push({ name: 'gallery' })">
            <template #icon><ArrowLeftOutlined /></template>
            返回图库
          </Button>
          <div class="shared-breadcrumb">共享图库 <span>/</span> 分享图片</div>
        </div>
        <Button
          v-if="isLoggedIn()"
          class="shared-download-button"
          type="primary"
          :loading="downloadLoading"
          :disabled="downloadLoading"
          @click="onDownload"
        >
          <template #icon><DownloadOutlined /></template>
          {{ downloadLabel }}
        </Button>
        <Button v-else class="shared-download-button" type="primary" @click="goToLogin">
          <template #icon><DownloadOutlined /></template>
          登录后下载
        </Button>
      </div>

      <div v-if="downloadStarted || downloadError" class="shared-download-status" :class="{ 'shared-download-status-error': !!downloadError }">
        <div class="shared-download-status-main" :role="downloadError ? 'alert' : 'status'" aria-live="polite">
          <template v-if="downloadStarted">
            <CloudDownloadOutlined />
            <span>下载已开始，浏览器正通过安全短期签名获取原图（{{ formatSize(image.picSize) }}）。</span>
          </template>
          <template v-else>
            <ExclamationCircleOutlined />
            <span>原图下载失败：{{ downloadError }}</span>
            <Button type="link" size="small" :loading="downloadLoading" @click="onDownload">重试</Button>
          </template>
        </div>
      </div>

      <div class="shared-detail">
        <div class="shared-media">
          <img
            v-if="image.url && !imageBroken"
            :src="image.url"
            :alt="image.name || '分享图片'"
            @error="onImageError"
          />
          <div v-else class="shared-media-fallback">
            <p>图片加载失败，签名地址可能已过期。</p>
            <Button @click="load">重新加载</Button>
          </div>
          <div class="shared-image-meta">
            <span>SHARED IMAGE · {{ formatDimensions(image.picWidth, image.picHeight) }}</span>
            <span>{{ image.picFormat || 'IMAGE' }}</span>
          </div>
        </div>

        <div class="shared-info">
          <p class="section-kicker">SHARED IMAGE</p>
          <h1 class="cp-page-title">{{ image.name || '未命名图片' }}</h1>
          <p class="shared-introduction">{{ image.introduction || '暂无简介' }}</p>

          <div v-if="image.source === 'PEXELS'" class="shared-pexels-attribution">
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

          <div v-if="image.tags.length" class="shared-tags">
            <Tag v-for="tag in image.tags" :key="tag">{{ tag }}</Tag>
          </div>

          <Descriptions :column="1" size="small" class="shared-descriptions">
            <DescriptionsItem label="分类">{{ image.category || '—' }}</DescriptionsItem>
            <DescriptionsItem label="尺寸">{{ formatDimensions(image.picWidth, image.picHeight) }}</DescriptionsItem>
            <DescriptionsItem label="格式">{{ image.picFormat || '—' }}</DescriptionsItem>
            <DescriptionsItem label="大小">{{ formatSize(image.picSize) }}</DescriptionsItem>
          </Descriptions>

          <p class="shared-download-hint">
            {{ isLoggedIn() ? '你已登录，可以下载原图。' : '登录后即可下载原图。' }}
          </p>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.shared-loading,
.shared-detail {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(320px, 0.9fr);
  gap: 20px;
  align-items: start;
}

.shared-loading :deep(.ant-skeleton) {
  padding-top: 24px;
}

.shared-skeleton-image {
  width: 100%;
  height: 360px;
}

.shared-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.shared-toolbar-leading {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.shared-back-link {
  padding-left: 0;
  flex: 0 0 auto;
  color: var(--cp-text-soft);
  font-size: 12px;
}

.shared-breadcrumb {
  overflow: hidden;
  color: var(--cp-text-muted);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.shared-breadcrumb span {
  padding: 0 6px;
  color: var(--cp-border);
}

.shared-download-button {
  flex: 0 0 auto;
  border-radius: var(--cp-radius);
}

.shared-download-status {
  padding: 12px 16px;
  margin-bottom: 20px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius);
  background: var(--cp-surface);
  color: var(--cp-status-approved-fg);
  font-size: 12px;
}

.shared-download-status-error {
  border-color: var(--cp-status-rejected-border);
  background: var(--cp-status-rejected-bg);
  color: var(--cp-status-rejected-fg);
}

.shared-download-status-main {
  display: flex;
  align-items: center;
  gap: 8px;
}

.shared-media {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 520px;
  overflow: hidden;
  border-radius: var(--cp-radius-lg);
  background: #202326;
  box-shadow: 0 12px 28px rgba(17, 24, 39, 0.1);
}

.shared-media img {
  display: block;
  width: 100%;
  max-height: 70vh;
  object-fit: contain;
}

.shared-media-fallback {
  padding: 48px 16px;
  color: #fff;
  text-align: center;
}

.shared-image-meta {
  position: absolute;
  right: 16px;
  bottom: 14px;
  left: 16px;
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

.shared-info {
  padding: 24px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius-lg);
  background: #fff;
  box-shadow: 0 8px 24px rgba(17, 24, 39, 0.04);
}

.shared-info .cp-page-title {
  font-size: 24px;
}

.shared-introduction {
  margin: 8px 0 12px;
  color: var(--cp-text-soft);
}

.shared-pexels-attribution {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 14px;
  color: var(--cp-text-soft);
  font-size: 12px;
}

.shared-pexels-attribution a {
  text-decoration: underline;
  text-underline-offset: 2px;
}

.shared-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 16px;
}

.shared-descriptions {
  padding-top: 16px;
  margin-bottom: 16px;
  border-top: 1px solid var(--cp-border-subtle);
}

.shared-descriptions :deep(.ant-descriptions-item-label) {
  color: var(--cp-text-soft);
  font-size: 12px;
}

.shared-descriptions :deep(.ant-descriptions-item-content) {
  color: var(--cp-text);
  font-size: 12px;
}

.shared-download-hint {
  margin: 0;
  color: var(--cp-text-soft);
  font-size: 12px;
}

@media (max-width: 899px) {
  .shared-loading,
  .shared-detail {
    grid-template-columns: 1fr;
  }

  .shared-media {
    min-height: 320px;
  }
}

@media (max-width: 575px) {
  .shared-toolbar {
    flex-wrap: wrap;
  }

  .shared-toolbar-leading {
    width: 100%;
    flex-wrap: wrap;
    gap: 4px 10px;
  }

  .shared-download-button {
    width: 100%;
  }

  .shared-media {
    min-height: 240px;
  }

  .shared-info {
    padding: 16px;
  }
}
</style>
