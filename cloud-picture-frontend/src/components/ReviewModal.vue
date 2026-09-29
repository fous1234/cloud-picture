<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Alert, Button, Form, FormItem, Modal, Radio, RadioGroup, Tag, Textarea, message } from 'ant-design-vue'
import { reviewImage } from '../api/admin-image'
import { errorMessage } from '../api/http'
import { reviewingImage, reviewInitial, bumpData } from '../stores/ui'

const open = computed({
  get: () => reviewingImage.value !== null,
  set: (value: boolean) => {
    if (!value) reviewingImage.value = null
  },
})

const submitting = ref(false)
const errorText = ref('')
const result = ref<1 | 2>(1)
const reason = ref('')

watch(reviewingImage, () => {
  submitting.value = false
  errorText.value = ''
  result.value = reviewInitial.value
  reason.value = ''
})

async function submit() {
  const image = reviewingImage.value
  if (!image || submitting.value) return
  if (result.value === 2 && !reason.value.trim()) {
    message.warning('拒绝时必须填写审核信息')
    return
  }
  if (reason.value.length > 512) {
    message.warning('审核信息不能超过 512 个字符')
    return
  }
  submitting.value = true
  errorText.value = ''
  try {
    await reviewImage({
      id: image.id,
      reviewStatus: result.value,
      reviewMessage: reason.value.trim() || undefined,
    })
    bumpData()
    message.success(result.value === 1 ? '已通过审核' : '已拒绝该图片')
    reviewingImage.value = null
  } catch (error) {
    errorText.value = errorMessage(error)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <Modal v-model:open="open" title="图片审核" :width="520" :mask-closable="false">
    <div v-if="reviewingImage" class="review-preview">
      <img :src="reviewingImage.thumbnailUrl || undefined" :alt="reviewingImage.name || '图片'" />
      <div>
        <div class="review-name">{{ reviewingImage.name || '未命名图片' }}</div>
        <div class="review-meta">
          <span v-if="reviewingImage.category">{{ reviewingImage.category }}</span>
          <span v-if="reviewingImage.owner?.name">上传者：{{ reviewingImage.owner.name }}</span>
        </div>
        <div class="review-tags">
          <Tag v-for="tag in reviewingImage.tags ?? []" :key="tag">{{ tag }}</Tag>
        </div>
      </div>
    </div>

    <Form layout="vertical">
      <FormItem label="审核结果">
        <RadioGroup v-model:value="result">
          <Radio :value="1">通过</Radio>
          <Radio :value="2">拒绝</Radio>
        </RadioGroup>
      </FormItem>
      <FormItem :label="result === 2 ? '拒绝理由（必填）' : '审核备注（可选）'">
        <Textarea
          v-model:value="reason"
          :rows="3"
          :maxlength="512"
          show-count
          :placeholder="result === 2 ? '请说明拒绝原因，用户可见' : '可填写补充说明'"
        />
      </FormItem>
    </Form>

    <Alert v-if="errorText" type="error" show-icon message="提交失败" :description="errorText" />

    <template #footer>
      <Button :disabled="submitting" @click="open = false">取消</Button>
      <Button type="primary" :loading="submitting" @click="submit">提交审核</Button>
    </template>
  </Modal>
</template>

<style scoped>
.review-preview {
  display: flex;
  gap: 12px;
  padding: 10px;
  margin-bottom: 16px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius);
}

.review-preview img {
  width: 96px;
  height: 96px;
  object-fit: cover;
  border-radius: 8px;
  background: var(--cp-bg-soft);
}

.review-name {
  font-weight: 600;
}

.review-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--cp-text-soft);
}

.review-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 6px;
}
</style>