<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Alert, AutoComplete, Button, Form, FormItem, Input, Modal, Select, Textarea, message } from 'ant-design-vue'
import { updateImage } from '../api/image'
import { errorMessage } from '../api/http'
import { editingImage, CATEGORY_OPTIONS, bumpData } from '../stores/ui'
import { normalizeTags, tagsTooLong } from '../utils/tags'

const open = computed({
  get: () => editingImage.value !== null,
  set: (value: boolean) => {
    if (!value) editingImage.value = null
  },
})

const submitting = ref(false)
const errorText = ref('')
const form = ref({
  name: '',
  introduction: '',
  category: undefined as string | undefined,
  tags: [] as string[],
})

watch(editingImage, (image) => {
  errorText.value = ''
  form.value = {
    name: image?.name ?? '',
    introduction: image?.introduction ?? '',
    category: image?.category ?? undefined,
    tags: [...(image?.tags ?? [])],
  }
})

async function submit() {
  const image = editingImage.value
  if (!image || submitting.value) return
  const tags = normalizeTags(form.value.tags)
  if (tagsTooLong(tags)) {
    message.error('标签总长度不能超过 512 个字符')
    return
  }
  submitting.value = true
  errorText.value = ''
  try {
    await updateImage({
      id: image.id,
      name: form.value.name.trim() || undefined,
      introduction: form.value.introduction.trim() || undefined,
      category: form.value.category || undefined,
      tags,
    })
    bumpData()
    message.success('图片信息已更新')
    editingImage.value = null
  } catch (error) {
    errorText.value = errorMessage(error)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <Modal v-model:open="open" title="编辑图片信息" :width="520" :mask-closable="false">
    <Form layout="vertical" :model="form">
      <FormItem label="标题" :rules="[{ max: 256, message: '标题不能超过 256 个字符' }]">
        <Input v-model:value="form.name" :maxlength="256" placeholder="图片标题" />
      </FormItem>
      <FormItem label="简介" :rules="[{ max: 512, message: '简介不能超过 512 个字符' }]">
        <Textarea v-model:value="form.introduction" :rows="3" :maxlength="512" show-count />
      </FormItem>
      <FormItem label="分类">
        <AutoComplete
          v-model:value="form.category"
          :options="CATEGORY_OPTIONS"
          placeholder="选择或输入分类"
          allow-clear
        />
      </FormItem>
      <FormItem label="标签">
        <Select
          v-model:value="form.tags"
          mode="tags"
          placeholder="输入后回车添加标签"
          :token-separators="[',', '，']"
          :open="false"
        />
      </FormItem>
    </Form>

    <Alert v-if="errorText" type="error" show-icon message="保存失败" :description="errorText" />

    <template #footer>
      <Button :disabled="submitting" @click="open = false">取消</Button>
      <Button type="primary" :loading="submitting" @click="submit">保存</Button>
    </template>
  </Modal>
</template>