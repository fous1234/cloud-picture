<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'
import {
  Alert,
  AutoComplete,
  Button,
  Form,
  FormItem,
  Input,
  Modal,
  Progress,
  Result,
  Select,
  Textarea,
  message,
} from 'ant-design-vue'
import {
  CheckCircleOutlined,
  CloudUploadOutlined,
  PlusOutlined,
  SwapOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons-vue'
import type { SpaceImageVO } from '../api/types'
import { generateImageMetadata, type AiImageMetadata } from '../api/ai'
import { uploadSpaceImage } from '../api/space'
import { errorMessage, ApiError } from '../api/http'
import { displayTag, normalizeTags, tagsTooLong, validateImageFile } from '../utils/tags'
import { CATEGORY_OPTIONS } from '../stores/ui'
import { formatSize } from '../utils/format'

const open = defineModel<boolean>('open', { required: true })
const emit = defineEmits<{ uploaded: []; quotaExceeded: [] }>()

const formRef = ref<{ validate: () => Promise<unknown> } | null>(null)
const file = ref<File | null>(null)
const previewUrl = ref('')
const dragging = ref(false)
const submitting = ref(false)
const percent = ref(0)
const errorText = ref('')
/** 后端配额兜底：前端预检被绕过时后端返回 SPACE_QUOTA_EXCEEDED，此时给出升级入口 */
const quotaExceeded = ref(false)
const uploaded = ref<SpaceImageVO | null>(null)
const aiMetadata = ref<AiImageMetadata | null>(null)
const aiLoading = ref(false)
const aiErrorText = ref('')
const aiDurationMs = ref<number | null>(null)
let aiRequestSeq = 0

const form = ref({
  name: '',
  introduction: '',
  category: undefined as string | undefined,
  tags: [] as string[],
})

function pickFile(event: Event) {
  const input = event.target as HTMLInputElement
  const picked = input.files?.[0]
  input.value = ''
  if (picked) accept(picked)
}

function onDrop(event: DragEvent) {
  dragging.value = false
  const files = event.dataTransfer?.files
  if (!files?.length) return
  if (files.length > 1) message.warning('仅支持单张图片上传，已使用第一张')
  accept(files[0])
}

function accept(picked: File) {
  const invalid = validateImageFile(picked)
  if (invalid) {
    message.error(invalid)
    return
  }
  clearAiState()
  revokePreview()
  file.value = picked
  previewUrl.value = URL.createObjectURL(picked)
  if (!form.value.name) form.value.name = picked.name.replace(/\.[^.]+$/, '')
}

function clearAiState() {
  aiRequestSeq += 1
  aiMetadata.value = null
  aiLoading.value = false
  aiErrorText.value = ''
  aiDurationMs.value = null
}

function clearFile() {
  clearAiState()
  revokePreview()
  file.value = null
  percent.value = 0
  errorText.value = ''
  quotaExceeded.value = false
}

function revokePreview() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
}

function reset() {
  clearFile()
  form.value = { name: '', introduction: '', category: undefined, tags: [] }
  submitting.value = false
  uploaded.value = null
}

watch(open, (value) => {
  if (!value && !submitting.value) reset()
})

onUnmounted(() => {
  revokePreview()
  aiRequestSeq += 1
})

function cleanSuggestedTags(tags: string[]) {
  const result: string[] = []
  let totalLength = 0
  for (const tag of normalizeTags(tags)) {
    if (tag.length > 32 || result.length >= 10 || totalLength + tag.length > 512) continue
    result.push(tag)
    totalLength += tag.length
  }
  return result
}

async function generateAi() {
  if (!file.value || aiLoading.value || submitting.value) return
  const requestFile = file.value
  const requestSeq = ++aiRequestSeq
  const startedAt = performance.now()
  aiLoading.value = true
  aiMetadata.value = null
  aiErrorText.value = ''
  aiDurationMs.value = null
  try {
    const result = await generateImageMetadata(requestFile)
    if (requestSeq !== aiRequestSeq || file.value !== requestFile || !open.value) return
    aiMetadata.value = {
      introduction: (result.introduction || '').trim(),
      tags: cleanSuggestedTags(result.tags || []),
    }
    aiDurationMs.value = Math.round(performance.now() - startedAt)
    message.success('已完成视觉分析，建议已更新')
  } catch (error) {
    if (requestSeq !== aiRequestSeq || file.value !== requestFile || !open.value) return
    aiErrorText.value = errorMessage(error)
  } finally {
    if (requestSeq === aiRequestSeq) aiLoading.value = false
  }
}

function formatAiDuration() {
  if (aiDurationMs.value === null) return ''
  return '耗时 ' + (aiDurationMs.value / 1000).toFixed(1) + 's'
}

function applyAiIntroduction() {
  if (!aiMetadata.value) return
  form.value.introduction = aiMetadata.value.introduction
  message.success('已成功采用 AI 推荐简介')
}

function replaceAiTags() {
  if (!aiMetadata.value) return
  form.value.tags = normalizeTags(aiMetadata.value.tags)
  message.success('已替换为 AI 标签列表')
}

function appendAiTags() {
  if (!aiMetadata.value) return
  form.value.tags = normalizeTags([...form.value.tags, ...aiMetadata.value.tags])
  message.success('已智能追加并去重标签')
}

async function submit() {
  if (submitting.value) return
  if (!file.value) {
    message.warning('请先选择一张图片')
    return
  }
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  const tags = normalizeTags(form.value.tags)
  if (tagsTooLong(tags)) {
    message.error('标签总长度不能超过 512 个字符')
    return
  }
  submitting.value = true
  errorText.value = ''
  quotaExceeded.value = false
  percent.value = 0
  try {
    uploaded.value = await uploadSpaceImage(
      file.value,
      {
        name: form.value.name.trim() || undefined,
        introduction: form.value.introduction.trim() || undefined,
        category: form.value.category || undefined,
        tags,
      },
      (value) => (percent.value = value),
    )
    emit('uploaded')
    message.success('上传成功，已保存到私有空间')
  } catch (error) {
    errorText.value = errorMessage(error)
    quotaExceeded.value = error instanceof ApiError && error.code === 'SPACE_QUOTA_EXCEEDED'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <Modal
    v-model:open="open"
    class="space-upload-modal"
    :width="820"
    :body-style="{ padding: 0 }"
    :closable="!submitting"
    :mask-closable="!submitting"
    :keyboard="!submitting"
  >
    <template #title>
      <div class="modal-title">
        <div class="modal-title-icon"><CloudUploadOutlined /></div>
        <div>
          <h2>上传到私有空间</h2>
          <p>私有图片免审核，仅自己可见</p>
        </div>
      </div>
    </template>

    <Result
      v-if="uploaded"
      status="success"
      title="上传成功，已保存到私有空间"
      :sub-title="'《' + (uploaded.name || '未命名图片') + '》已存入私有空间，仅自己可见'"
    >
      <template #extra>
        <Button @click="reset">继续上传</Button>
        <Button type="primary" @click="open = false">完成</Button>
      </template>
    </Result>

    <div v-else class="space-upload-body">
      <div
        v-if="!file"
        class="dropzone"
        :class="{ 'dropzone-active': dragging }"
        @dragover.prevent="dragging = true"
        @dragleave.prevent="dragging = false"
        @drop.prevent="onDrop"
      >
        <input
          id="space-upload-file-input"
          class="visually-hidden"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          :disabled="submitting"
          @change="pickFile"
        />
        <label for="space-upload-file-input" class="dropzone-label">
          <CloudUploadOutlined class="dropzone-icon" />
          <strong>点击选择图片</strong>
          <span class="dropzone-hint">或将图片拖放到此处 · 单张 JPEG / PNG / WebP，不超过 10 MB</span>
        </label>
      </div>

      <div v-if="file" class="selected-image-card">
        <div class="selected-image-thumb">
          <img :src="previewUrl" :alt="file.name" />
        </div>
        <div class="selected-image-info">
          <div class="selected-image-heading">
            <strong>{{ file.name }}</strong>
            <span>{{ file.type.split('/')[1]?.toUpperCase() || 'IMAGE' }} · {{ formatSize(file.size) }}</span>
          </div>
          <p>图片将只保存在你的私有空间，不会进入共享图库。</p>
          <div class="selected-image-footer">
            <span class="validated"><CheckCircleOutlined /> 图片已通过预校验</span>
            <Button type="text" :disabled="submitting" @click="clearFile">
              <template #icon><SwapOutlined /></template>
              更换图片
            </Button>
          </div>
        </div>
      </div>

      <section v-if="file" class="ai-section">
        <div class="ai-section-head">
          <div class="ai-section-copy">
            <div class="ai-section-title">
              <ThunderboltOutlined />
              <h3>AI 智能信息助手</h3>
              <span>智能识别</span>
            </div>
            <p>根据画面光影、建筑构图与季相特征，快速生成专业摄影归档描述。</p>
          </div>
          <Button
            type="primary"
            class="ai-generate-button"
            :loading="aiLoading"
            :disabled="submitting"
            @click="generateAi"
          >
            <template #icon><ThunderboltOutlined v-if="!aiLoading" /></template>
            {{ aiLoading ? '百炼正在深度理解画面...' : aiMetadata ? '✨ 重新生成 AI 建议' : '✨ 生成 AI 建议' }}
          </Button>
        </div>
        <Alert
          v-if="aiErrorText"
          class="ai-error"
          type="error"
          show-icon
          :message="aiErrorText"
          description="可以重试，也可以跳过 AI 直接填写并上传。"
        />
      </section>

      <section v-if="aiMetadata" class="ai-recommendation">
        <div class="ai-recommendation-head">
          <div>
            <div class="ai-recommendation-title">✨ AI 智能视觉助手建议</div>
            <div class="ai-recommendation-subtitle">
              独立建议区 · 未应用<span v-if="aiDurationMs !== null"> · {{ formatAiDuration() }}</span>
            </div>
          </div>
        </div>
        <div class="ai-introduction-row">
          <div class="ai-label">AI 推荐简介</div>
          <div class="ai-introduction-text">{{ aiMetadata.introduction || '未生成可用简介' }}</div>
          <Button size="small" @click="applyAiIntroduction">使用 AI 简介</Button>
        </div>
        <div class="ai-tags-row">
          <div class="ai-tags-heading">
            <span class="ai-label">AI 推荐标签（{{ aiMetadata.tags.length }}项）</span>
            <div class="ai-tag-actions">
              <Button size="small" @click="replaceAiTags">
                <template #icon><SwapOutlined /></template>
                替换为 AI 标签
              </Button>
              <Button size="small" type="primary" @click="appendAiTags">
                <template #icon><PlusOutlined /></template>
                追加 AI 标签
              </Button>
            </div>
          </div>
          <div v-if="aiMetadata.tags.length" class="ai-tags">
            <span v-for="tag in aiMetadata.tags" :key="tag" class="ai-tag-pill">{{ displayTag(tag) }}</span>
          </div>
          <span v-else class="ai-empty-tags">未生成可用标签</span>
        </div>
      </section>

      <Form ref="formRef" layout="vertical" :model="form" class="upload-form">
        <div class="form-row">
          <FormItem
            class="form-title"
            label="作品标题"
            name="name"
            :rules="[{ max: 256, message: '标题不能超过 256 个字符' }]"
          >
            <Input v-model:value="form.name" placeholder="输入摄影作品标题..." :maxlength="256" />
          </FormItem>
          <FormItem class="form-category" label="归属分类" name="category">
            <AutoComplete
              v-model:value="form.category"
              :options="CATEGORY_OPTIONS"
              placeholder="选择或输入分类"
              allow-clear
            />
          </FormItem>
        </div>
        <FormItem
          label="作品简介"
          name="introduction"
          :rules="[{ max: 512, message: '简介不能超过 512 个字符' }]"
        >
          <Textarea
            v-model:value="form.introduction"
            :rows="3"
            :maxlength="512"
            show-count
            placeholder="输入关于此画面的拍摄说明、心境或光影背景..."
          />
        </FormItem>
        <FormItem label="检索标签 (Tags)" name="tags">
          <div class="tag-field-hint">回车键或逗号添加新标签</div>
          <Select
            v-model:value="form.tags"
            class="tag-select-display"
            mode="tags"
            placeholder="+ 添加标签..."
            :token-separators="[',', '，']"
            :open="false"
          />
        </FormItem>
      </Form>

      <Progress v-if="submitting" :percent="percent" status="active" />

      <Alert
        v-if="errorText"
        class="upload-error"
        type="error"
        show-icon
        message="上传失败"
        :description="errorText + '（已保留所选文件和填写内容，可重试）'"
      >
        <template v-if="quotaExceeded" #action>
          <Button size="small" type="primary" @click="emit('quotaExceeded')">去升级套餐</Button>
        </template>
      </Alert>
    </div>

    <template v-if="!uploaded" #footer>
      <Button :disabled="submitting" @click="open = false">取消</Button>
      <Button type="primary" :loading="submitting" @click="submit">
        {{ submitting ? '上传中' : '确认并上传到私有空间' }}
      </Button>
    </template>
  </Modal>
</template>

<style scoped>
.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  margin: -1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}

.modal-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.modal-title-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: var(--cp-bg-soft);
  color: var(--cp-text);
  font-size: 20px;
}

.modal-title h2,
.modal-title p {
  margin: 0;
}

.modal-title h2 {
  color: var(--cp-text);
  font-family: 'Plus Jakarta Sans', Inter, sans-serif;
  font-size: 15px;
  font-weight: 600;
  line-height: 20px;
}

.modal-title p {
  margin-top: 2px;
  color: var(--cp-text-muted);
  font-size: 12px;
  line-height: 18px;
}

.space-upload-body {
  display: flex;
  flex-direction: column;
  gap: 24px;
  max-height: calc(86vh - 140px);
  padding: 32px;
  overflow-y: auto;
}

:deep(.ant-modal-header) {
  margin-bottom: 0;
  padding: 24px 32px 16px;
}

:deep(.ant-modal-content) {
  border-radius: 12px;
}

:deep(.ant-modal-footer) {
  padding: 14px 32px 20px;
}

.dropzone {
  min-height: 180px;
  margin: 0;
  border: 2px dashed #9ca3af;
  border-radius: 12px;
  background: #f9fafb;
  transition: border-color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease;
}

.dropzone-active,
.dropzone:hover {
  border-color: var(--cp-accent);
  background: var(--cp-bg-soft);
}

.dropzone:focus-within {
  border-color: var(--cp-accent);
  box-shadow: 0 0 0 3px rgba(17, 24, 39, 0.08);
}

.dropzone-label {
  display: flex;
  min-height: 180px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px 24px;
  cursor: pointer;
  color: var(--cp-text);
  text-align: center;
}

.dropzone-label strong {
  font-size: 16px;
  font-weight: 600;
}

.dropzone-icon {
  font-size: 40px;
  color: var(--cp-text-soft);
}

.dropzone-hint {
  color: var(--cp-text-soft);
  font-size: 12px;
  line-height: 18px;
}

.selected-image-card {
  display: flex;
  gap: 24px;
  padding: 16px;
  border-radius: 12px;
  background: var(--cp-bg-soft);
}

.selected-image-thumb {
  position: relative;
  flex: 0 0 176px;
  height: 144px;
  overflow: hidden;
  border-radius: 8px;
  background: var(--cp-border);
}

.selected-image-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.selected-image-info {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  justify-content: space-between;
}

.selected-image-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.selected-image-heading strong {
  overflow: hidden;
  color: var(--cp-text);
  font-size: 15px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.selected-image-heading span {
  flex: 0 0 auto;
  padding: 2px 4px;
  border-radius: 4px;
  background: var(--cp-bg);
  color: var(--cp-text-soft);
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
}

.selected-image-info p {
  margin: 8px 0 0;
  color: var(--cp-text-soft);
  font-size: 12px;
  line-height: 18px;
}

.selected-image-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-top: 8px;
}

.validated {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--cp-status-approved-fg);
  font-size: 12px;
}

.ai-section,
.ai-recommendation {
  border-radius: 12px;
}

.ai-section {
  padding: 24px;
  background: var(--cp-bg-soft);
}

.ai-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.ai-section-copy {
  min-width: 0;
}

.ai-section-title {
  display: flex;
  align-items: center;
  gap: 6px;
}

.ai-section-title h3 {
  margin: 0;
  color: var(--cp-text);
  font-size: 15px;
  font-weight: 600;
}

.ai-section-title span {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--cp-border-subtle);
  color: var(--cp-text-soft);
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
}

.ai-section-copy p {
  margin: 4px 0 0;
  color: var(--cp-text-soft);
  font-size: 12px;
  line-height: 18px;
}

.ai-generate-button {
  flex: 0 0 auto;
}

.ai-error {
  margin-top: 16px;
}

.ai-recommendation {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--cp-border-subtle);
  background: var(--cp-surface);
}

.ai-recommendation-title {
  color: var(--cp-text);
  font-size: 14px;
  font-weight: 600;
}

.ai-recommendation-subtitle {
  margin-top: 2px;
  color: var(--cp-text-muted);
  font-size: 11px;
}

.ai-introduction-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.ai-label {
  color: var(--cp-text-muted);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.ai-introduction-text {
  flex: 1;
  color: var(--cp-text);
  font-size: 14px;
  line-height: 20px;
}

.ai-tags-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ai-tags-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.ai-tag-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.ai-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.ai-tag-pill {
  padding: 4px 12px;
  border-radius: 999px;
  background: var(--cp-border);
  color: var(--cp-text);
  font-size: 12px;
}

.ai-empty-tags {
  color: var(--cp-text-muted);
  font-size: 12px;
}

.upload-form {
  margin: 0;
}

.form-row {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
}

.form-title,
.form-category {
  min-width: 0;
}

.upload-form :deep(.ant-form-item-label > label) {
  color: var(--cp-text-soft);
  font-size: 12px;
  font-weight: 600;
}

.upload-form :deep(.ant-input),
.upload-form :deep(.ant-input-affix-wrapper),
.upload-form :deep(.ant-select-selector),
.upload-form :deep(.ant-input-textarea textarea) {
  border-color: var(--cp-border);
  background: var(--cp-bg-soft);
  border-radius: 8px;
}

.upload-form :deep(.ant-input),
.upload-form :deep(.ant-select-selector) {
  min-height: 44px;
}

.upload-form :deep(.ant-input-textarea textarea) {
  padding: 16px;
}

.tag-field-hint {
  float: right;
  margin-top: -32px;
  color: var(--cp-text-muted);
  font-size: 12px;
}

.tag-select-display :deep(.ant-select-selection-item-content)::before {
  color: var(--cp-text-soft);
  content: '#';
}

.upload-error {
  margin: 0;
}

@media (max-width: 767px) {
  .space-upload-body {
    padding: 24px 16px;
  }

  :deep(.ant-modal-header) {
    padding: 20px 16px 14px;
  }

  :deep(.ant-modal-footer) {
    padding: 12px 16px 16px;
  }

  .selected-image-card,
  .ai-section-head,
  .ai-introduction-row {
    align-items: stretch;
    flex-direction: column;
  }

  .selected-image-thumb {
    flex-basis: auto;
    width: 100%;
    height: 180px;
  }

  .ai-generate-button {
    width: 100%;
  }

  .ai-tags-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .form-row {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .tag-field-hint {
    float: none;
    margin: -4px 0 8px;
  }
}

@media (max-width: 575px) {
  :deep(.ant-modal) {
    max-width: calc(100vw - 16px);
    margin: 8px auto;
  }

  .space-upload-body {
    max-height: calc(100vh - 150px);
  }
}
</style>