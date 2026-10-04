<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Button, Popover, message } from 'ant-design-vue'
import {
  CheckCircleOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  ExclamationCircleOutlined,
  ExpandOutlined,
  EyeOutlined,
  ShareAltOutlined,
  StopOutlined,
} from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import type { ImageVO } from '../api/types'
import { downloadImage } from '../api/image'
import { errorMessage } from '../api/http'
import { REVIEW_STATUS_TEXT } from '../stores/ui'
import { formatDateTime, formatDimensions, formatSize } from '../utils/format'
import ImageSharePanel from './ImageSharePanel.vue'

const props = defineProps<{
  image: ImageVO | null
  mode: 'review' | 'mine'
  reviewing?: boolean
}>()

const emit = defineEmits<{
  approve: [ImageVO]
  reject: [ImageVO, string]
  edit: [ImageVO]
  delete: [ImageVO]
}>()

const router = useRouter()

const previewBroken = ref(false)
const rejectOpen = ref(false)
const rejectReason = ref('')
const shareOpen = ref(false)
const downloading = ref(false)

const REJECT_TEMPLATES = [
  { label: '清晰度不足', reason: '清晰度不足，存在可见失焦或过度压缩噪点。' },
  { label: '包含未授权肖像', reason: '画面包含未授权的特写肖像，需补充肖像权授权声明。' },
  { label: '分类标签不准', reason: '所选分类或标签与画面核心内容不符。' },
  { label: '画质未达标', reason: '原图尺寸未达到 2K 规格准入基准。' },
]

const STATUS_CODE: Record<number, string> = {
  0: 'pending_review',
  1: 'approved',
  2: 'rejected',
}

const previewUrl = computed(() => props.image?.url || props.image?.thumbnailUrl || '')
const sizeLabel = computed(() => {
  const current = props.image
  if (!current) return ''
  const dimensions = formatDimensions(current.picWidth, current.picHeight)
  return dimensions === '—'
    ? formatSize(current.picSize)
    : `${dimensions} PX · ${formatSize(current.picSize)}`
})
const kicker = computed(() => {
  const current = props.image
  if (!current) return ''
  const prefix = current.category ? `${current.category.toUpperCase()} / ` : ''
  return `${prefix}ID: ${current.id}`
})
const canApprove = computed(() => !!props.image && props.image.reviewStatus !== 1)
const canReject = computed(() => !!props.image && props.image.reviewStatus !== 2)
const canShare = computed(() => props.image?.reviewStatus === 1)

watch(
  () => props.image?.id,
  () => {
    previewBroken.value = false
    rejectOpen.value = false
    rejectReason.value = ''
    shareOpen.value = false
  },
)

function approve() {
  if (!props.image || !canApprove.value || props.reviewing) return
  emit('approve', props.image)
}

function openReject() {
  if (!props.image || !canReject.value) return
  rejectOpen.value = true
}

function confirmReject() {
  const current = props.image
  const reason = rejectReason.value.trim()
  if (!current || !reason || props.reviewing) return
  emit('reject', current, reason)
}

function goDetail() {
  if (props.image) router.push(`/image/${props.image.id}`)
}

async function onDownload() {
  const current = props.image
  if (!current || downloading.value) return
  downloading.value = true
  try {
    const url = await downloadImage(current.id)
    const link = document.createElement('a')
    link.href = url
    link.download = ''
    link.style.display = 'none'
    document.body.appendChild(link)
    link.click()
    link.remove()
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    downloading.value = false
  }
}

function onKeydown(event: KeyboardEvent) {
  if (props.mode !== 'review') return
  const target = document.activeElement?.tagName
  if (target === 'INPUT' || target === 'TEXTAREA') return
  if (event.key === 'a' || event.key === 'A') approve()
  else if (event.key === 'r' || event.key === 'R') openReject()
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <section class="detail">
    <template v-if="image">
      <div class="detail-stage">
        <div class="detail-stage-size">
          <ExpandOutlined />
          <span>{{ sizeLabel }}</span>
        </div>
        <img
          v-if="previewUrl && !previewBroken"
          :src="previewUrl"
          :alt="image.name || '图片预览'"
          @error="previewBroken = true"
        />
        <span v-else class="detail-stage-fallback">图片加载失败</span>
      </div>

      <div class="detail-panel">
        <div class="detail-meta-top">
          <span class="detail-kicker">{{ kicker }}</span>
          <span class="detail-status-code">
            {{ REVIEW_STATUS_TEXT[image.reviewStatus] }} status: {{ STATUS_CODE[image.reviewStatus] }}
          </span>
        </div>

        <div class="detail-title-row">
          <h2>{{ image.name || '未命名图片' }}</h2>
          <span v-if="image.category" class="detail-category">{{ image.category }}</span>
        </div>

        <div class="detail-intro">{{ image.introduction || '暂无简介' }}</div>

        <div v-if="image.tags?.length" class="detail-tags-block">
          <span class="detail-tags-label">检索标签</span>
          <div class="detail-tags">
            <span v-for="tag in image.tags" :key="tag" class="detail-tag">#{{ tag }}</span>
          </div>
        </div>

        <div class="detail-grid">
          <div class="detail-grid-cell">
            <span>分辨率</span>
            <strong>{{ formatDimensions(image.picWidth, image.picHeight) }}</strong>
          </div>
          <div class="detail-grid-cell">
            <span>文件大小</span>
            <strong>{{ formatSize(image.picSize) }}</strong>
          </div>
          <div class="detail-grid-cell">
            <span>图片格式</span>
            <strong>{{ (image.picFormat || 'IMAGE').toUpperCase() }}</strong>
          </div>
          <div class="detail-grid-cell">
            <span>提交时间</span>
            <strong>{{ formatDateTime(image.createTime) }}</strong>
          </div>
        </div>

        <div v-if="mode === 'review' && image.owner" class="detail-owner-card">
          <span class="detail-owner-avatar">{{ (image.owner.name || 'U').slice(0, 1) }}</span>
          <span class="detail-owner-name">{{ image.owner.name || '未知用户' }}</span>
        </div>

        <div
          v-if="mode === 'mine' && image.reviewStatus === 2 && image.reviewMessage"
          class="detail-feedback"
        >
          <ExclamationCircleOutlined />
          <span>{{ image.reviewMessage }}</span>
        </div>

        <p v-if="image.source === 'PEXELS'" class="detail-pexels">
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

        <!-- 审核决策栏 -->
        <div v-if="mode === 'review'" class="detail-decision">
          <div class="detail-decision-head">
            <span class="detail-decision-title">审核决策流转</span>
            <span class="detail-kbd">
              快捷键：
              <kbd>A</kbd> 通过
              <span class="detail-kbd-sep">/</span>
              <kbd>R</kbd> 拒绝
            </span>
          </div>

          <div class="detail-decision-actions">
            <Button
              v-if="canApprove"
              class="detail-approve"
              block
              :loading="reviewing"
              @click="approve"
            >
              <template #icon><CheckCircleOutlined /></template>
              通过入库并公开
            </Button>
            <Button
              v-if="canReject"
              class="detail-reject"
              block
              :disabled="reviewing"
              @click="openReject"
            >
              <template #icon><StopOutlined /></template>
              拒绝申请
            </Button>
          </div>

          <div v-if="rejectOpen" class="detail-reject-box">
            <div class="detail-reject-head">
              <span class="detail-reject-title">
                <ExclamationCircleOutlined />
                填写拒绝原因 (必填)
              </span>
              <span class="detail-reject-note">创作者将收到通知与修改指导</span>
            </div>
            <div class="detail-reject-templates">
              <span>快捷理由模板（点击快速填入）：</span>
              <div class="detail-reject-chips">
                <button
                  v-for="template in REJECT_TEMPLATES"
                  :key="template.label"
                  type="button"
                  @click="rejectReason = template.reason"
                >
                  {{ template.label }}
                </button>
              </div>
            </div>
            <textarea
              v-model="rejectReason"
              rows="3"
              placeholder="请输入具体修改建议与驳回原因..."
            />
            <div class="detail-reject-actions">
              <Button :disabled="reviewing" @click="rejectOpen = false">取消并收起</Button>
              <Button
                danger
                type="primary"
                :disabled="!rejectReason.trim()"
                :loading="reviewing"
                @click="confirmReject"
              >
                确认拒绝
              </Button>
            </div>
          </div>
        </div>

        <!-- 我的上传操作栏 -->
        <div v-else class="detail-actions">
          <Button @click="emit('edit', image)">
            <template #icon><EditOutlined /></template>
            编辑信息
          </Button>
          <Button @click="goDetail">
            <template #icon><EyeOutlined /></template>
            查看详情
          </Button>
          <Popover v-model:open="shareOpen" trigger="click" placement="topRight">
            <template #content>
              <div class="detail-share-popover">
                <ImageSharePanel v-if="shareOpen && image" :image-id="image.id" />
              </div>
            </template>
            <Button
              :disabled="!canShare"
              :title="canShare ? undefined : '仅审核通过的图片可分享'"
            >
              <template #icon><ShareAltOutlined /></template>
              分享
            </Button>
          </Popover>
          <Button :loading="downloading" @click="onDownload">
            <template #icon><DownloadOutlined /></template>
            下载
          </Button>
          <Button danger @click="emit('delete', image)">
            <template #icon><DeleteOutlined /></template>
            删除
          </Button>
        </div>
      </div>
    </template>

    <div v-else class="detail-placeholder">从左侧选择一张图片查看详情</div>
  </section>
</template>

<style scoped>
.detail {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-stage {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 460px;
  max-height: 580px;
  border: 1px solid #262626;
  border-radius: var(--cp-radius-lg);
  background: #0d1117;
  box-shadow: var(--cp-shadow-card);
  overflow: hidden;
}

.detail-stage img {
  display: block;
  width: 100%;
  height: 100%;
  max-height: 540px;
  padding: 16px;
  object-fit: contain;
  user-select: none;
}

.detail-stage-fallback {
  color: #fff;
  font-size: 12px;
}

.detail-stage-size {
  position: absolute;
  top: 16px;
  left: 16px;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: var(--cp-radius);
  background: rgba(0, 0, 0, 0.75);
  color: rgba(255, 255, 255, 0.95);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}

.detail-stage-size :deep(.anticon) {
  color: #34d399;
  font-size: 15px;
}

.detail-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 24px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-surface);
  box-shadow: var(--cp-shadow-subtle);
}

.detail-meta-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
}

.detail-kicker {
  color: var(--cp-text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.detail-status-code {
  padding: 2px 10px;
  border: 1px solid var(--cp-status-approved-border);
  border-radius: 999px;
  background: var(--cp-status-approved-bg);
  color: var(--cp-status-approved-fg);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
  font-weight: 600;
}

.detail-title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail-title-row h2 {
  margin: 0;
  color: var(--cp-text);
  font-size: 24px;
  font-weight: 700;
}

.detail-category {
  padding: 4px 12px;
  border: 1px solid var(--cp-border);
  border-radius: 999px;
  background: var(--cp-bg-soft);
  color: var(--cp-text);
  font-size: 12px;
  font-weight: 600;
}

.detail-intro {
  padding: 16px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius);
  background: var(--cp-bg-soft);
  color: var(--cp-text-soft);
  font-size: 14px;
  line-height: 1.7;
}

.detail-tags-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-tags-label {
  color: var(--cp-text-muted);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.08em;
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.detail-tag {
  padding: 4px 12px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius);
  background: var(--cp-bg-soft);
  color: var(--cp-text);
  font-size: 12px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius);
  background: var(--cp-bg-soft);
}

.detail-grid-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.detail-grid-cell span {
  color: var(--cp-text-muted);
  font-size: 11px;
}

.detail-grid-cell strong {
  color: var(--cp-text);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  font-weight: 600;
}

.detail-owner-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius);
  background: var(--cp-surface);
  box-shadow: var(--cp-shadow-subtle);
}

.detail-owner-avatar {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 999px;
  background: var(--cp-bg-soft);
  color: var(--cp-text-soft);
  font-size: 15px;
  font-weight: 600;
}

.detail-owner-name {
  color: var(--cp-text);
  font-size: 14px;
  font-weight: 700;
}

.detail-feedback {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--cp-status-rejected-border);
  border-radius: var(--cp-radius);
  background: var(--cp-status-rejected-bg);
  color: var(--cp-status-rejected-fg);
  font-size: 12px;
  line-height: 1.6;
}

.detail-pexels {
  margin: 0;
  color: var(--cp-text-soft);
  font-size: 12px;
}

.detail-pexels a {
  text-decoration: underline;
  text-underline-offset: 2px;
}

.detail-decision {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-top: 8px;
  border-top: 1px solid var(--cp-border);
}

.detail-decision-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 12px;
}

.detail-decision-title {
  color: var(--cp-text-soft);
  font-weight: 500;
}

.detail-kbd {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--cp-text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.detail-kbd kbd {
  padding: 2px 6px;
  border: 1px solid var(--cp-border);
  border-radius: 4px;
  background: var(--cp-bg-soft);
  color: var(--cp-text);
  font-family: inherit;
  font-weight: 600;
  box-shadow: var(--cp-shadow-subtle);
}

.detail-kbd-sep {
  color: var(--cp-text-muted);
}

.detail-decision-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.detail-decision-actions :deep(.ant-btn) {
  height: 48px;
  flex: 1 1 200px;
  border-radius: var(--cp-radius);
  font-size: 14px;
  font-weight: 700;
}

.detail-approve,
.detail-approve:hover {
  border-color: transparent;
  background: var(--cp-status-approved-fg);
  color: #fff;
}

/* 实心深红；ant 默认按钮的 hover 规则优先级更高，会把文字/边框改回主题色，故用 !important 固定 */
.detail-reject,
.detail-reject:hover {
  border-color: transparent !important;
  background: var(--cp-status-rejected-fg) !important;
  color: #fff !important;
}

.detail-reject:hover {
  background: #b91c1c !important;
}

.detail-reject-box {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--cp-status-rejected-border);
  border-radius: var(--cp-radius);
  background: color-mix(in srgb, var(--cp-status-rejected-bg) 60%, #fff);
}

.detail-reject-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail-reject-title {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--cp-status-rejected-fg);
  font-size: 12px;
  font-weight: 700;
}

.detail-reject-note {
  color: var(--cp-text-muted);
  font-size: 11px;
}

.detail-reject-templates {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: var(--cp-text-soft);
  font-size: 11px;
}

.detail-reject-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.detail-reject-chips button {
  padding: 4px 10px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius);
  background: var(--cp-surface);
  color: var(--cp-text-soft);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  box-shadow: var(--cp-shadow-subtle);
}

.detail-reject-chips button:hover {
  background: var(--cp-bg-soft);
}

.detail-reject-box textarea {
  width: 100%;
  padding: 12px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius);
  background: var(--cp-surface);
  color: var(--cp-text);
  font: inherit;
  font-size: 12px;
  resize: none;
  box-shadow: var(--cp-shadow-subtle);
}

.detail-reject-box textarea:focus {
  outline: none;
  border-color: var(--cp-status-rejected-fg);
}

.detail-reject-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding-top: 8px;
  border-top: 1px solid var(--cp-border);
}

.detail-actions :deep(.ant-btn) {
  border-radius: var(--cp-radius);
}

.detail-placeholder {
  display: grid;
  place-items: center;
  min-height: 320px;
  border: 1px dashed var(--cp-border);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-bg-soft);
  color: var(--cp-text-muted);
  font-size: 13px;
}

.detail-share-popover {
  width: 420px;
}

@media (max-width: 991px) {
  .detail-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .detail-stage {
    min-height: 340px;
  }
}

@media (max-width: 575px) {
  .detail-panel {
    padding: 16px;
  }

  .detail-stage {
    min-height: 240px;
  }

  .detail-decision-actions :deep(.ant-btn),
  .detail-actions :deep(.ant-btn) {
    flex: 1 1 100%;
  }

  .detail-share-popover {
    width: min(84vw, 380px);
  }
}
</style>