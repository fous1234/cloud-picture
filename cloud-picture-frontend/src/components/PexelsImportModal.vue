<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  Alert,
  Button,
  Form,
  FormItem,
  Input,
  InputNumber,
  Modal,
  Select,
  message,
} from 'ant-design-vue'
import { importPexelsImages } from '../api/pexels-import'
import type { ImportResult } from '../api/pexels-import'
import { ApiError, errorMessage } from '../api/http'
import { bumpData, pexelsImportOpen } from '../stores/ui'
import { normalizeTags, tagsTooLong } from '../utils/tags'

const submitting = ref(false)
const router = useRouter()
const errorText = ref('')
const warning = ref(false)
const result = ref<ImportResult | null>(null)
const formRef = ref<{ validate: () => Promise<unknown> } | null>(null)
const open = computed({
  get: () => pexelsImportOpen.value,
  set: (value: boolean) => {
    if (!value) close()
  },
})
const form = ref({
  keyword: '',
  count: 5 as number | undefined,
  category: '',
  tags: [] as string[],
})

function reset() {
  form.value = { keyword: '', count: 5, category: '', tags: [] }
  errorText.value = ''
  warning.value = false
  result.value = null
}

watch(pexelsImportOpen, (open) => {
  if (open && !submitting.value) reset()
})

function close() {
  pexelsImportOpen.value = false
  if (!submitting.value) reset()
}

function returnToImageManagement() {
  close()
  router.push({ name: 'image-management', query: { tab: 'mine' } })
}

function importSummary(value: ImportResult) {
  return `成功导入 ${value.imported} 张，跳过 ${value.skipped} 张，失败 ${value.failed} 张。`
}

async function submit() {
  if (submitting.value || result.value) return
  errorText.value = ''
  warning.value = false
  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  const keyword = form.value.keyword.trim()
  const tags = normalizeTags(form.value.tags)
  if (!tags.length) tags.push(keyword)
  if (tagsTooLong(tags)) {
    errorText.value = '标签总长度不能超过 512 个字符'
    return
  }

  submitting.value = true
  try {
    result.value = await importPexelsImages({
      keyword,
      count: form.value.count ?? 5,
      category: form.value.category.trim() || undefined,
      tags,
    })
    bumpData()
  } catch (error) {
    if (error instanceof ApiError && error.code === 'NETWORK_ERROR') {
      warning.value = true
      bumpData()
      if (!pexelsImportOpen.value) {
        message.warning('导入耗时较长或网络中断，请检查当前列表中的结果，再决定是否重试。')
      }
    } else if (error instanceof ApiError && (error.code === 'PEXELS_ERROR' || error.status === 502)) {
      errorText.value = 'Pexels 暂时不可用或配额不足，请稍后重试'
    } else if (error instanceof ApiError && error.status === 401) {
      return
    } else if (error instanceof ApiError && error.status === 403) {
      errorText.value = '没有导入权限'
    } else if (error instanceof ApiError && error.status === 400) {
      errorText.value = errorMessage(error)
    } else {
      errorText.value = '导入失败，请稍后重试'
    }
    if (!pexelsImportOpen.value && errorText.value) message.error(errorText.value)
  } finally {
    submitting.value = false
    if (!pexelsImportOpen.value) reset()
  }
}
</script>

<template>
  <Modal
    v-model:open="open"
    title="从 Pexels 导入"
    :width="600"
    @cancel="close"
  >
    <template v-if="result">
      <div class="import-result-heading">导入完成</div>
      <div class="import-result-stats">
        <div><span>成功导入</span><strong>{{ result.imported }}</strong></div>
        <div><span>跳过</span><strong>{{ result.skipped }}</strong></div>
        <div :class="{ 'import-stat-failed': result.failed > 0 }">
          <span>失败</span><strong>{{ result.failed }}</strong>
        </div>
      </div>
      <Alert
        v-if="result.failed > 0"
        class="import-result-message"
        type="warning"
        show-icon
        :message="importSummary(result)"
        description="可以减少导入数量后重试。"
      />
      <Alert
        v-else-if="result.imported === 0 && result.skipped > 0"
        class="import-result-message"
        type="info"
        show-icon
        message="这些图片已经导入过"
      />
      <p v-else class="import-result-message">{{ importSummary(result) }}</p>
    </template>

    <Form v-else ref="formRef" layout="vertical" :model="form">
      <FormItem
        label="关键词"
        name="keyword"
        :rules="[
          { required: true, whitespace: true, message: '请输入搜索关键词' },
          { max: 64, message: '关键词不能超过 64 个字符' },
        ]"
      >
        <Input v-model:value="form.keyword" :maxlength="64" :disabled="submitting" placeholder="例如：校园风景" />
      </FormItem>
      <FormItem
        label="导入数量"
        name="count"
        :rules="[
          { required: true, type: 'number', message: '请输入导入数量' },
          { type: 'number', min: 1, max: 10, message: '导入数量需在 1 到 10 之间' },
        ]"
      >
        <InputNumber
          v-model:value="form.count"
          :min="1"
          :max="10"
          :precision="0"
          :disabled="submitting"
          style="width: 100%"
        />
      </FormItem>
      <FormItem label="分类" name="category" :rules="[{ max: 64, message: '分类不能超过 64 个字符' }]">
        <Input v-model:value="form.category" :maxlength="64" :disabled="submitting" placeholder="可选" />
      </FormItem>
      <FormItem label="标签" name="tags">
        <Select
          v-model:value="form.tags"
          mode="tags"
          :token-separators="[',', '，']"
          :open="false"
          :disabled="submitting"
          placeholder="可选，未填写时自动使用关键词"
        />
      </FormItem>
      <p class="import-form-note">
        图片将下载后保存到本站 COS。仅用于毕业设计演示，请保留 Pexels 来源署名。
      </p>
      <p class="import-form-note">本次最多导入 10 张，导入过程可能需要几十秒。</p>
      <Alert
        v-if="warning"
        class="import-form-message"
        type="warning"
        show-icon
        message="导入耗时较长或网络中断"
        description="请先检查当前列表中的导入结果，再决定是否重试。"
      />
      <Alert
        v-if="errorText"
        class="import-form-message"
        type="error"
        show-icon
        message="导入失败"
        :description="errorText"
      />
    </Form>

    <template #footer>
      <template v-if="result">
        <Button type="primary" @click="returnToImageManagement">返回图片管理</Button>
      </template>
      <template v-else>
        <Button :disabled="submitting" @click="close">取消</Button>
        <Button type="primary" :loading="submitting" @click="submit">开始导入</Button>
      </template>
    </template>
  </Modal>
</template>

<style scoped>
.import-result-heading {
  margin-bottom: 16px;
  font-size: 18px;
  font-weight: 600;
}

.import-result-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.import-result-stats > div {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius);
  background: var(--cp-bg);
  text-align: center;
}

.import-result-stats span {
  color: var(--cp-text-soft);
  font-size: 12px;
}

.import-result-stats strong {
  font-size: 24px;
}

.import-result-stats .import-stat-failed strong {
  color: #cf1322;
}

.import-result-message,
.import-form-message {
  margin-top: 14px;
}

.import-form-note {
  margin: 4px 0;
  color: var(--cp-text-soft);
  font-size: 12px;
}
</style>
