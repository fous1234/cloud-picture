<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Alert, Button, Input, Modal, Spin, message } from 'ant-design-vue'
import {
  CopyOutlined,
  DeleteOutlined,
  LinkOutlined,
  QrcodeOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue'
import QRCode from 'qrcode'
import type { Id } from '../api/types'
import { createImageShare, getImageShare, revokeImageShare } from '../api/image'
import { errorMessage } from '../api/http'

/** 分享区（生成/复制链接/二维码/撤销/重新生成），详情页与图片管理详情共用；挂载即加载，卸载即复位 */
const props = defineProps<{ imageId: Id }>()

const loading = ref(true)
const creating = ref(false)
const regenerating = ref(false)
const revoking = ref(false)
const token = ref<string | null>(null)
const qrCode = ref<string | null>(null)
const error = ref<string | null>(null)
const qrError = ref<string | null>(null)
let requestSequence = 0
let qrRequestSequence = 0

const busy = computed(
  () => loading.value || creating.value || regenerating.value || revoking.value,
)
const shareUrl = computed(() =>
  token.value ? `${window.location.origin}/share/${encodeURIComponent(token.value)}` : '',
)

async function updateQr(value: string) {
  const seq = ++qrRequestSequence
  qrCode.value = null
  qrError.value = null
  try {
    const dataUrl = await QRCode.toDataURL(
      `${window.location.origin}/share/${encodeURIComponent(value)}`,
      { width: 144, margin: 1, errorCorrectionLevel: 'M' },
    )
    if (seq === qrRequestSequence && token.value === value) qrCode.value = dataUrl
  } catch {
    if (seq === qrRequestSequence && token.value === value) {
      qrError.value = '二维码暂不可用，你仍可以复制分享链接。'
    }
  }
}

async function load() {
  const seq = ++requestSequence
  loading.value = true
  error.value = null
  try {
    const share = await getImageShare(props.imageId)
    if (seq !== requestSequence) return
    token.value = share.enabled ? share.token : null
    if (token.value) await updateQr(token.value)
  } catch (e) {
    if (seq === requestSequence) error.value = errorMessage(e)
  } finally {
    if (seq === requestSequence) loading.value = false
  }
}

async function create(regenerate = false) {
  if (busy.value) return
  const seq = requestSequence
  if (regenerate) regenerating.value = true
  else creating.value = true
  error.value = null
  try {
    const share = await createImageShare(props.imageId)
    if (seq !== requestSequence) return
    token.value = share.enabled ? share.token : null
    if (!token.value) throw new Error('分享链接生成失败，请重试')
    await updateQr(token.value)
    message.success(regenerate ? '分享链接已重新生成' : '分享链接已生成')
  } catch (e) {
    if (seq === requestSequence) error.value = errorMessage(e)
  } finally {
    if (seq === requestSequence) {
      creating.value = false
      regenerating.value = false
    }
  }
}

function confirmRegenerate() {
  Modal.confirm({
    title: '重新生成分享链接？',
    content: '重新生成后，之前的分享链接将立即失效。',
    okText: '重新生成',
    cancelText: '取消',
    onOk: () => create(true),
  })
}

async function revoke() {
  if (!token.value || busy.value) return
  const seq = requestSequence
  revoking.value = true
  error.value = null
  try {
    const revoked = await revokeImageShare(props.imageId)
    if (seq !== requestSequence) return
    if (!revoked) throw new Error('分享链接未能撤销，请重试')
    token.value = null
    qrCode.value = null
    qrError.value = null
    message.success('分享链接已撤销')
  } catch (e) {
    if (seq === requestSequence) error.value = errorMessage(e)
  } finally {
    if (seq === requestSequence) revoking.value = false
  }
}

function confirmRevoke() {
  Modal.confirm({
    title: '撤销分享链接？',
    content: '撤销后，已发出的链接将无法继续访问。',
    okText: '撤销分享',
    cancelText: '取消',
    okButtonProps: { danger: true },
    onOk: revoke,
  })
}

async function copyLink() {
  if (!shareUrl.value) return
  try {
    await navigator.clipboard.writeText(shareUrl.value)
    message.success('链接已复制，可以粘贴到微信分享')
  } catch {
    message.warning('复制失败，请手动选择并复制分享链接')
  }
}

onMounted(load)
watch(
  () => props.imageId,
  () => load(),
)
</script>

<template>
  <div class="share-panel">
    <div v-if="loading" class="share-panel-loading">
      <Spin />
      <span>正在获取分享链接</span>
    </div>

    <template v-else>
      <Alert v-if="error" type="error" show-icon :message="error" />

      <template v-if="token">
        <div class="share-link-row">
          <Input :value="shareUrl" readonly aria-label="分享链接" />
          <Button class="share-copy-button" type="primary" :disabled="busy" @click="copyLink">
            <template #icon><CopyOutlined /></template>
            复制链接
          </Button>
        </div>

        <div class="share-qr-card">
          <div class="share-qr-frame">
            <img v-if="qrCode" :src="qrCode" alt="图片分享链接二维码" />
            <Spin v-else-if="!qrError" />
            <QrcodeOutlined v-else class="share-qr-fallback" />
          </div>
          <div class="share-qr-copy">
            <span class="share-qr-label">微信扫码即可查看</span>
            <strong>分享图片给更多人</strong>
            <p>访客无需登录即可预览，下载原图仍需登录。</p>
          </div>
        </div>

        <Alert
          v-if="qrError"
          class="share-qr-warning"
          type="warning"
          show-icon
          :message="qrError"
        />

        <div class="share-panel-notice">
          <LinkOutlined />
          <span>链接长期有效；撤销或重新生成后，旧链接将失效。</span>
        </div>

        <div class="share-panel-actions">
          <Button :disabled="busy" :loading="regenerating" @click="confirmRegenerate">
            <template #icon><ReloadOutlined /></template>
            重新生成
          </Button>
          <Button danger :disabled="busy" :loading="revoking" @click="confirmRevoke">
            <template #icon><DeleteOutlined /></template>
            撤销分享
          </Button>
        </div>
      </template>

      <div v-else-if="!error" class="share-panel-empty">
        <p>为这张已审核通过的图片创建一个可转发的分享链接。</p>
        <Button type="primary" :loading="creating" :disabled="busy" @click="create()">
          <template #icon><LinkOutlined /></template>
          生成分享链接
        </Button>
      </div>

      <Button v-if="error" class="share-retry-button" :loading="loading" @click="load">
        重试
      </Button>
    </template>
  </div>
</template>

<style scoped>
.share-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.share-panel-loading {
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

.share-panel-notice {
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

.share-panel-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.share-panel-empty {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--cp-border-subtle);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-surface);
}

.share-panel-empty p {
  margin: 0;
  color: var(--cp-text-soft);
  font-size: 13px;
}

.share-retry-button {
  align-self: flex-start;
}

@media (max-width: 575px) {
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

  .share-panel-actions {
    flex-wrap: wrap;
  }
}
</style>