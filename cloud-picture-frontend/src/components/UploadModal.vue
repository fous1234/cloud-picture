<script setup lang="ts">
import { onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
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
import { CloudUploadOutlined, DeleteOutlined } from '@ant-design/icons-vue'
import type { ImageVO } from '../api/types'
import { uploadImage } from '../api/image'
import { errorMessage } from '../api/http'
import { normalizeTags, tagsTooLong, validateImageFile } from '../utils/tags'
import { CATEGORY_OPTIONS, bumpData } from '../stores/ui'
import { formatSize } from '../utils/format'

const open = defineModel<boolean>('open', { required: true })

const router = useRouter()

const formRef = ref<{ validate: () => Promise<unknown> } | null>(null)

const file = ref<File | null>(null)
const previewUrl = ref('')
const dragging = ref(false)
const submitting = ref(false)
const percent = ref(0)
const errorText = ref('')
const uploaded = ref<ImageVO | null>(null)

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
  revokePreview()
  file.value = picked
  previewUrl.value = URL.createObjectURL(picked)
  if (!form.value.name) form.value.name = picked.name.replace(/\.[^.]+$/, '')
}

function revokePreview() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  previewUrl.value = ''
}

function reset() {
  revokePreview()
  file.value = null
  form.value = { name: '', introduction: '', category: undefined, tags: [] }
  submitting.value = false
  percent.value = 0
  errorText.value = ''
  uploaded.value = null
}

watch(open, (value) => {
  if (!value && !submitting.value) reset()
})

onUnmounted(revokePreview)

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
  percent.value = 0
  try {
    const result = await uploadImage(
      file.value,
      {
        name: form.value.name.trim() || undefined,
        introduction: form.value.introduction.trim() || undefined,
        category: form.value.category || undefined,
        tags,
      },
      (value) => (percent.value = value),
    )
    uploaded.value = result
    bumpData()
    message.success(uploadResultMessage(result))
  } catch (error) {
    // 失败时保留已选文件与表单，用户可直接重试
    errorText.value = errorMessage(error)
  } finally {
    submitting.value = false
  }
}

function uploadResultMessage(image: ImageVO) {
  return image.reviewStatus === 1 ? '上传成功，图片已发布' : '上传成功，等待管理员审核'
}

function viewDetail() {
  const id = uploaded.value?.id
  open.value = false
  if (id) router.push(`/image/${id}`)
}
</script>

<template>
  <Modal
    v-model:open="open"
    title="上传新图片"
    :width="720"
    :closable="!submitting"
    :mask-closable="!submitting"
    :keyboard="!submitting"
  >
    <Result
      v-if="uploaded"
      status="success"
      :title="uploadResultMessage(uploaded)"
      :sub-title="uploaded.reviewStatus === 1 ? '图片已发布到共享图库' : `《${uploaded.name || '未命名图片'}》已提交审核`"
    >
      <template #extra>
        <Button type="primary" @click="viewDetail">查看详情</Button>
        <Button @click="reset">继续上传</Button>
      </template>
    </Result>

    <template v-else>
      <div
        v-if="!file"
        class="dropzone"
        :class="{ 'dropzone-active': dragging }"
        @dragover.prevent="dragging = true"
        @dragleave.prevent="dragging = false"
        @drop.prevent="onDrop"
      >
        <input
          id="upload-file-input"
          class="visually-hidden"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          :disabled="submitting"
          @change="pickFile"
        />
        <label for="upload-file-input" class="dropzone-label">
          <CloudUploadOutlined class="dropzone-icon" />
          <span>点击选择图片，或将图片拖放到此处</span>
          <span class="dropzone-hint">单张 JPEG / PNG / WebP，不超过 10 MB</span>
        </label>
      </div>

      <div v-if="file" class="file-preview">
        <img :src="previewUrl" :alt="file.name" />
        <div class="file-info">
          <div class="file-name">{{ file.name }}</div>
          <div class="file-size">{{ formatSize(file.size) }}</div>
        </div>
        <Button type="text" danger :disabled="submitting" @click="reset">
          <template #icon><DeleteOutlined /></template>
          移除
        </Button>
      </div>

      <Form ref="formRef" layout="vertical" :model="form" class="upload-form">
        <FormItem
          label="标题"
          name="name"
          :rules="[{ max: 256, message: '标题不能超过 256 个字符' }]"
        >
          <Input v-model:value="form.name" placeholder="不填则使用文件名" :maxlength="256" />
        </FormItem>
        <FormItem
          label="简介"
          name="introduction"
          :rules="[{ max: 512, message: '简介不能超过 512 个字符' }]"
        >
          <Textarea
            v-model:value="form.introduction"
            :rows="3"
            :maxlength="512"
            show-count
            placeholder="简单描述这张图片"
          />
        </FormItem>
        <FormItem label="分类" name="category">
          <AutoComplete
            v-model:value="form.category"
            :options="CATEGORY_OPTIONS"
            placeholder="选择或输入分类"
            allow-clear
          />
        </FormItem>
        <FormItem label="标签" name="tags">
          <Select
            v-model:value="form.tags"
            mode="tags"
            placeholder="输入后回车添加标签，如：校园、风景"
            :token-separators="[',', '，']"
            :open="false"
          />
        </FormItem>
      </Form>

      <Progress v-if="submitting" :percent="percent" :status="'active'" />

      <Alert
        v-if="errorText"
        class="upload-error"
        type="error"
        show-icon
        message="上传失败"
        :description="`${errorText}（已保留所选文件和填写内容，可重试）`"
      />
    </template>

    <template v-if="!uploaded" #footer>
      <Button :disabled="submitting" @click="open = false">取消</Button>
      <Button type="primary" :loading="submitting" @click="submit">
        {{ submitting ? '上传中' : '上传' }}
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

.dropzone {
  border: 1px dashed var(--cp-border);
  border-radius: var(--cp-radius);
  background: var(--cp-bg-soft);
  margin-bottom: 16px;
  transition: border-color 0.2s ease, background 0.2s ease;
}

.dropzone-active {
  border-color: var(--cp-accent);
  background: #f1f3f5;
}

.dropzone:focus-within {
  border-color: var(--cp-accent);
}

.dropzone-label {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 28px 16px;
  cursor: pointer;
  text-align: center;
}

.dropzone-icon {
  font-size: 26px;
  color: var(--cp-text-soft);
}

.dropzone-hint {
  font-size: 12px;
  color: var(--cp-text-soft);
}

.file-preview {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px;
  margin-bottom: 16px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius);
}

.file-preview img {
  width: 64px;
  height: 64px;
  object-fit: cover;
  border-radius: 8px;
  background: var(--cp-bg-soft);
}

.file-info {
  flex: 1;
  min-width: 0;
}

.file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.file-size {
  font-size: 12px;
  color: var(--cp-text-soft);
}

.upload-form {
  margin-bottom: 0;
}

.upload-error {
  margin-top: 12px;
}

@media (max-width: 575px) {
  :deep(.ant-modal) {
    max-width: calc(100vw - 24px);
    margin: 12px auto;
  }
}
</style>
