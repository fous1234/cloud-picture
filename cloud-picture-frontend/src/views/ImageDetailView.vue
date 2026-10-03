<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Alert,
  Avatar,
  Button,
  Descriptions,
  DescriptionsItem,
  Input,
  Modal,
  Result,
  Skeleton,
  Spin,
  Tag,
  message,
} from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  CloudDownloadOutlined,
  CopyOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  ExclamationCircleOutlined,
  LinkOutlined,
  QrcodeOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  ShareAltOutlined,
} from '@ant-design/icons-vue'
import QRCode from 'qrcode'
import type { ImageVO } from '../api/types'
import { createImageShare, downloadImage, getImage, getImageShare, revokeImageShare } from '../api/image'
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
const downloadLoading = ref(false)
const downloadStarted = ref(false)
const downloadError = ref<string | null>(null)
const shareModalOpen = ref(false)
const shareLoading = ref(false)
const shareCreating = ref(false)
const shareRegenerating = ref(false)
const shareRevoking = ref(false)
const shareToken = ref<string | null>(null)
const shareQrCode = ref<string | null>(null)
const shareError = ref<string | null>(null)
const shareQrError = ref<string | null>(null)
/** 签名地址失效只自动重取一次，再失败就交给用户手动重试 */
let refreshedOnce = false
let downloadRequestSequence = 0
let shareRequestSequence = 0
let shareQrRequestSequence = 0

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
const canShare = computed(() => !!image.value && image.value.reviewStatus === 1 && isOwner(image.value))
const shareBusy = computed(
  () => shareLoading.value || shareCreating.value || shareRegenerating.value || shareRevoking.value,
)
const shareUrl = computed(() =>
  shareToken.value
    ? `${window.location.origin}/share/${encodeURIComponent(shareToken.value)}`
    : '',
)

function resetDownloadState() {
  downloadRequestSequence++
  downloadLoading.value = false
  downloadStarted.value = false
  downloadError.value = null
}

async function load() {
  resetDownloadState()
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

async function onDownload() {
  const currentImage = image.value
  if (!currentImage || downloadLoading.value) return

  const requestSequence = ++downloadRequestSequence
  downloadLoading.value = true
  downloadStarted.value = false
  downloadError.value = null

  try {
    const url = await downloadImage(currentImage.id)
    if (requestSequence !== downloadRequestSequence || id.value !== currentImage.id) return

    const link = document.createElement('a')
    link.href = url
    link.download = ''
    link.style.display = 'none'
    document.body.appendChild(link)
    link.click()
    link.remove()
    downloadStarted.value = true
  } catch (e) {
    if (requestSequence === downloadRequestSequence && id.value === currentImage.id) {
      downloadError.value = errorMessage(e)
    }
  } finally {
    if (requestSequence === downloadRequestSequence) downloadLoading.value = false
  }
}

function resetShareState() {
  shareRequestSequence++
  shareQrRequestSequence++
  shareLoading.value = false
  shareCreating.value = false
  shareRegenerating.value = false
  shareRevoking.value = false
  shareToken.value = null
  shareQrCode.value = null
  shareError.value = null
  shareQrError.value = null
}

function closeShareModal() {
  shareModalOpen.value = false
  resetShareState()
}

async function updateShareQr(token: string) {
  const requestSequence = ++shareQrRequestSequence
  shareQrCode.value = null
  shareQrError.value = null
  try {
    const qrCode = await QRCode.toDataURL(`${window.location.origin}/share/${encodeURIComponent(token)}`, {
      width: 144,
      margin: 1,
      errorCorrectionLevel: 'M',
    })
    if (requestSequence === shareQrRequestSequence && shareToken.value === token) {
      shareQrCode.value = qrCode
    }
  } catch {
    if (requestSequence === shareQrRequestSequence && shareToken.value === token) {
      shareQrError.value = '二维码暂不可用，你仍可以复制分享链接。'
    }
  }
}

async function loadShareState() {
  const currentImage = image.value
  if (!currentImage || !canShare.value) return

  const requestSequence = ++shareRequestSequence
  shareLoading.value = true
  shareError.value = null
  try {
    const share = await getImageShare(currentImage.id)
    if (requestSequence !== shareRequestSequence || id.value !== currentImage.id) return
    shareToken.value = share.enabled ? share.token : null
    if (shareToken.value) await updateShareQr(shareToken.value)
  } catch (e) {
    if (requestSequence === shareRequestSequence && id.value === currentImage.id) {
      shareError.value = errorMessage(e)
    }
  } finally {
    if (requestSequence === shareRequestSequence) shareLoading.value = false
  }
}

function openShareModal() {
  if (!canShare.value || shareBusy.value) return
  shareModalOpen.value = true
  void loadShareState()
}

async function onCreateShare(regenerating = false) {
  const currentImage = image.value
  if (!currentImage || !canShare.value || shareBusy.value) return

  const requestSequence = shareRequestSequence
  if (regenerating) shareRegenerating.value = true
  else shareCreating.value = true
  shareError.value = null
  try {
    const share = await createImageShare(currentImage.id)
    if (requestSequence !== shareRequestSequence || id.value !== currentImage.id) return
    shareToken.value = share.enabled ? share.token : null
    if (!shareToken.value) throw new Error('分享链接生成失败，请重试')
    await updateShareQr(shareToken.value)
    message.success(regenerating ? '分享链接已重新生成' : '分享链接已生成')
  } catch (e) {
    if (requestSequence === shareRequestSequence && id.value === currentImage.id) {
      shareError.value = errorMessage(e)
    }
  } finally {
    if (requestSequence === shareRequestSequence) {
      shareCreating.value = false
      shareRegenerating.value = false
    }
  }
}

function confirmRegenerateShare() {
  Modal.confirm({
    title: '重新生成分享链接？',
    content: '重新生成后，之前的分享链接将立即失效。',
    okText: '重新生成',
    cancelText: '取消',
    onOk: () => onCreateShare(true),
  })
}

async function onRevokeShare() {
  const currentImage = image.value
  if (!currentImage || !shareToken.value || shareBusy.value) return

  const requestSequence = shareRequestSequence
  shareRevoking.value = true
  shareError.value = null
  try {
    const revoked = await revokeImageShare(currentImage.id)
    if (requestSequence !== shareRequestSequence || id.value !== currentImage.id) return
    if (!revoked) throw new Error('分享链接未能撤销，请重试')
    shareToken.value = null
    shareQrCode.value = null
    shareQrError.value = null
    message.success('分享链接已撤销')
  } catch (e) {
    if (requestSequence === shareRequestSequence && id.value === currentImage.id) {
      shareError.value = errorMessage(e)
    }
  } finally {
    if (requestSequence === shareRequestSequence) shareRevoking.value = false
  }
}

function confirmRevokeShare() {
  Modal.confirm({
    title: '撤销分享链接？',
    content: '撤销后，已发出的链接将无法继续访问。',
    okText: '撤销分享',
    cancelText: '取消',
    okButtonProps: { danger: true },
    onOk: onRevokeShare,
  })
}

async function copyShareLink() {
  if (!shareUrl.value) return
  try {
    await navigator.clipboard.writeText(shareUrl.value)
    message.success('链接已复制，可以粘贴到微信分享')
  } catch {
    message.warning('复制失败，请手动选择并复制分享链接')
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
  closeShareModal()
  load()
}, { immediate: true })
watch(dataVersion, load)
watch(canShare, (allowed) => {
  if (!allowed && shareModalOpen.value) closeShareModal()
})
</script>

<template>
  <div class="cp-container cp-page">
    <div class="detail-toolbar">
      <div class="detail-toolbar-leading">
        <Button class="back-link" type="link" @click="router.push({ name: 'gallery' })">
          <template #icon><ArrowLeftOutlined /></template>
          返回图库
        </Button>
        <div v-if="image" class="detail-breadcrumb">
          共享图库 <span>/</span> {{ image.category || '图片' }} <span>/</span> 详情
        </div>
      </div>
      <div v-if="image && !loading" class="detail-toolbar-actions">
        <Button v-if="canShare" class="share-button" @click="openShareModal">
          <template #icon><ShareAltOutlined /></template>
          分享
        </Button>
        <Button
          class="download-button"
          type="primary"
          :loading="downloadLoading"
          :disabled="downloadLoading"
          @click="onDownload"
        >
          <template #icon><DownloadOutlined /></template>
          <span>原图下载（{{ formatSize(image.picSize) }}）</span>
          <span class="download-available">可下载</span>
        </Button>
      </div>
    </div>

    <Modal
      v-model:open="shareModalOpen"
      title="分享图片"
      :width="560"
      :footer="null"
      :mask-closable="!shareBusy"
      wrap-class-name="share-modal-wrap"
      @cancel="closeShareModal"
    >
      <div class="share-modal-content">
        <p class="share-modal-description">复制链接或使用微信扫码，分享这张图片。</p>

        <div v-if="shareLoading" class="share-modal-loading">
          <Spin />
          <span>正在获取分享链接</span>
        </div>

        <template v-else>
          <Alert v-if="shareError" type="error" show-icon :message="shareError" />

          <template v-if="shareToken">
            <div class="share-link-row">
              <Input :value="shareUrl" readonly aria-label="分享链接" />
              <Button class="share-copy-button" type="primary" :disabled="shareBusy" @click="copyShareLink">
                <template #icon><CopyOutlined /></template>
                复制链接
              </Button>
            </div>

            <div class="share-qr-card">
              <div class="share-qr-frame">
                <img v-if="shareQrCode" :src="shareQrCode" alt="图片分享链接二维码" />
                <Spin v-else-if="!shareQrError" />
                <QrcodeOutlined v-else class="share-qr-fallback" />
              </div>
              <div class="share-qr-copy">
                <span class="share-qr-label">微信扫码即可查看</span>
                <strong>分享图片给更多人</strong>
                <p>访客无需登录即可预览，下载原图仍需登录。</p>
              </div>
            </div>

            <Alert
              v-if="shareQrError"
              class="share-qr-warning"
              type="warning"
              show-icon
              :message="shareQrError"
            />

            <div class="share-modal-notice">
              <LinkOutlined />
              <span>链接长期有效；撤销或重新生成后，旧链接将失效。</span>
            </div>

            <div class="share-modal-actions">
              <Button :disabled="shareBusy" :loading="shareRegenerating" @click="confirmRegenerateShare">
                <template #icon><ReloadOutlined /></template>
                重新生成
              </Button>
              <Button danger :disabled="shareBusy" :loading="shareRevoking" @click="confirmRevokeShare">
                <template #icon><DeleteOutlined /></template>
                撤销分享
              </Button>
            </div>
          </template>

          <div v-else-if="!shareError" class="share-empty-state">
            <p>为这张已审核通过的图片创建一个可转发的分享链接。</p>
            <Button type="primary" :loading="shareCreating" :disabled="shareBusy" @click="onCreateShare()">
              <template #icon><LinkOutlined /></template>
              生成分享链接
            </Button>
          </div>

          <Button v-if="shareError" class="share-retry-button" :loading="shareLoading" @click="loadShareState">
            重试
          </Button>
        </template>
      </div>
    </Modal>

    <div
      v-if="image && (downloadStarted || downloadError)"
      class="download-status"
      :class="{ 'download-status-error': !!downloadError }"
      :role="downloadError ? 'alert' : 'status'"
      aria-live="polite"
    >
      <div class="download-status-main">
        <template v-if="downloadStarted">
          <CloudDownloadOutlined class="download-status-icon" />
          <span>下载已开始，浏览器正通过安全短期签名获取原图文件（{{ formatSize(image.picSize) }}）。</span>
        </template>
        <template v-else>
          <ExclamationCircleOutlined class="download-status-icon" />
          <span>原图下载失败：{{ downloadError }}</span>
          <Button type="link" size="small" :loading="downloadLoading" @click="onDownload">重试</Button>
        </template>
      </div>
      <div v-if="downloadStarted" class="download-permission">
        <SafetyCertificateOutlined />
        <span>原图权限已由后端校验</span>
      </div>
    </div>

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
.detail-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.detail-toolbar-leading {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.detail-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}

.back-link {
  padding-left: 0;
  flex: 0 0 auto;
  color: var(--cp-text-soft);
  font-size: 12px;
}

.detail-breadcrumb {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--cp-text-muted);
  font-size: 11px;
}

.detail-breadcrumb span {
  padding: 0 6px;
  color: var(--cp-border);
}

.download-button {
  flex: 0 0 auto;
  border-radius: var(--cp-radius);
}

.share-button {
  border-radius: var(--cp-radius);
}

.share-modal-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.share-modal-description {
  margin: 0;
  color: var(--cp-text-soft);
  font-size: 13px;
}

.share-modal-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 100px;
  color: var(--cp-text-soft);
}

.share-link-row {
  display: flex;
  gap: 8px;
}

.share-copy-button {
  flex: 0 0 auto;
  border-radius: var(--cp-radius);
}

.share-qr-card {
  display: grid;
  grid-template-columns: 144px minmax(0, 1fr);
  align-items: center;
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--cp-border-subtle);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-surface);
}

.share-qr-frame {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 144px;
  height: 144px;
  border-radius: var(--cp-radius);
  background: #fff;
}

.share-qr-frame img {
  display: block;
  width: 144px;
  height: 144px;
}

.share-qr-fallback {
  color: var(--cp-text-muted);
  font-size: 28px;
}

.share-qr-copy {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.share-qr-label {
  color: var(--cp-status-approved-fg);
  font-size: 12px;
}

.share-qr-copy strong {
  color: var(--cp-text);
  font-size: 15px;
}

.share-qr-copy p {
  margin: 0;
  color: var(--cp-text-soft);
  font-size: 12px;
  line-height: 1.6;
}

.share-qr-warning {
  margin-top: -8px;
}

.share-modal-notice {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--cp-border-subtle);
  border-radius: var(--cp-radius);
  background: var(--cp-surface);
  color: var(--cp-text-soft);
  font-size: 12px;
  line-height: 1.6;
}

.share-modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.share-empty-state {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--cp-border-subtle);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-surface);
}

.share-empty-state p {
  margin: 0;
  color: var(--cp-text-soft);
  font-size: 13px;
}

.share-retry-button {
  align-self: flex-start;
}

.download-available {
  margin-left: 4px;
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--cp-status-approved-bg);
  color: var(--cp-text);
  font-size: 10px;
  font-weight: 600;
  line-height: 1.2;
}

.download-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 16px;
  margin-bottom: 20px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius);
  background: var(--cp-surface);
  font-size: 12px;
}

.download-status-main,
.download-permission {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.download-status-icon,
.download-permission {
  color: var(--cp-status-approved-fg);
}

.download-status-error {
  border-color: var(--cp-status-rejected-border);
  background: var(--cp-status-rejected-bg);
  color: var(--cp-status-rejected-fg);
}

.download-status-error .download-status-icon {
  color: var(--cp-status-rejected-fg);
}

.download-permission {
  flex: 0 0 auto;
  color: var(--cp-text-soft);
  font-size: 11px;
}

.detail {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(320px, 0.9fr);
  gap: 20px;
  align-items: start;
}

.detail-skeleton-image {
  width: 100%;
  height: 360px;
}

.detail-media {
  position: relative;
  background: #202326;
  border-radius: var(--cp-radius-lg);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 520px;
  box-shadow: 0 12px 28px rgba(17, 24, 39, 0.1);
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

.detail-info {
  padding: 24px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius-lg);
  background: #fff;
  box-shadow: 0 8px 24px rgba(17, 24, 39, 0.04);
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

.detail-heading .cp-page-title {
  font-size: 24px;
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
  padding-top: 16px;
  margin-bottom: 20px;
  border-top: 1px solid var(--cp-border-subtle);
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

.detail-actions :deep(.ant-btn) {
  border-radius: var(--cp-radius);
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
  .detail-toolbar {
    flex-wrap: wrap;
  }

  .detail-toolbar-leading {
    width: 100%;
    flex-wrap: wrap;
    gap: 4px 10px;
  }

  .detail-toolbar-actions {
    width: 100%;
  }

  .download-button {
    flex: 1;
    min-width: 0;
  }

  :global(.share-modal-wrap .ant-modal) {
    max-width: calc(100vw - 24px);
    margin: 12px auto;
  }

  .share-link-row {
    flex-direction: column;
  }

  .share-copy-button {
    width: 100%;
  }

  .share-qr-card {
    grid-template-columns: 1fr;
    justify-items: center;
    text-align: center;
  }

  .share-modal-actions {
    flex-wrap: wrap;
  }

  .download-status {
    align-items: flex-start;
    flex-direction: column;
  }

  .download-permission {
    padding-left: 26px;
  }

  .detail-media {
    min-height: 240px;
  }

  .detail-info {
    padding: 16px;
  }
}
</style>
