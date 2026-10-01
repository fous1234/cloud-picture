<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Alert, Button, Pagination, RadioButton, RadioGroup, Spin } from 'ant-design-vue'
import type { ImageVO, ReviewStatus } from '../api/types'
import { listMyImages } from '../api/image'
import { errorMessage } from '../api/http'
import ImageGrid from '../components/ImageGrid.vue'
import { dataVersion, openPexelsImport, openUpload } from '../stores/ui'
import { isAdmin } from '../stores/session'
import { confirmDeleteImage } from '../utils/imageActions'

const route = useRoute()
const router = useRouter()

const DEFAULT_PAGE_SIZE = 12

const images = ref<ImageVO[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref<string | null>(null)

let requestSeq = 0

const STATUS_OPTIONS = [
  { value: 'all', label: '全部' },
  { value: '0', label: '待审核' },
  { value: '1', label: '已通过' },
  { value: '2', label: '已拒绝' },
]

function readQuery() {
  const status = !isAdmin() && typeof route.query.status === 'string' ? route.query.status : 'all'
  const current = Number(route.query.current) > 0 ? Number(route.query.current) : 1
  const size = Number(route.query.size) > 0 ? Number(route.query.size) : DEFAULT_PAGE_SIZE
  return { status, current, size }
}

const status = computed({
  get: () => readQuery().status,
  set: (value: string) => {
    const query: Record<string, string | number> = {}
    if (value !== 'all') query.status = value
    router.push({ name: 'my-images', query })
  },
})

const reviewStatusParam = computed<ReviewStatus | undefined>(() => {
  if (isAdmin()) return undefined
  const value = readQuery().status
  return value === 'all' ? undefined : (Number(value) as ReviewStatus)
})

async function load() {
  const { current, size } = readQuery()
  const seq = ++requestSeq
  loading.value = true
  error.value = null
  try {
    const page = await listMyImages({
      current,
      size,
      reviewStatus: reviewStatusParam.value,
    })
    if (seq !== requestSeq) return
    images.value = page.records ?? []
    total.value = page.total ?? 0
  } catch (e) {
    if (seq !== requestSeq) return
    images.value = []
    total.value = 0
    error.value = errorMessage(e)
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

watch(() => route.query, load, { immediate: true })
watch(dataVersion, load)

function changePage(page: number, size: number) {
  const query: Record<string, string | number> = { current: page }
  if (readQuery().status !== 'all') query.status = readQuery().status
  if (size !== DEFAULT_PAGE_SIZE) query.size = size
  router.push({ name: 'my-images', query })
}

const emptyText = computed(() =>
  isAdmin() || readQuery().status === 'all' ? '还没有上传图片' : '该状态下没有图片',
)
</script>

<template>
  <div class="cp-container cp-page">
    <div class="page-head">
      <div>
        <p class="section-kicker">PERSONAL COLLECTION</p>
        <h1 class="cp-page-title">我的上传</h1>
        <p class="cp-page-subtitle">
          {{ isAdmin() ? '查看本人上传的图片，维护图片信息，删除不再需要的图片' : '查看审核状态、维护图片信息，删除不再需要的图片' }}
        </p>
      </div>
      <Button v-if="isAdmin()" type="primary" @click="openPexelsImport">从 Pexels 导入</Button>
    </div>

    <div class="cp-toolbar">
      <RadioGroup v-if="!isAdmin()" v-model:value="status" button-style="solid">
        <RadioButton v-for="option in STATUS_OPTIONS" :key="option.value" :value="option.value">
          {{ option.label }}
        </RadioButton>
      </RadioGroup>
      <span class="toolbar-count">共 {{ total }} 张</span>
    </div>

    <template v-if="error">
      <Alert type="error" show-icon message="加载失败" :description="error">
        <template #action>
          <Button size="small" @click="load">重试</Button>
        </template>
      </Alert>
    </template>

    <Spin v-else :spinning="loading && images.length > 0">
      <div class="my-upload-grid">
        <ImageGrid
          :loading="loading && !images.length"
          :images="images"
          :empty-text="emptyText"
          :show-status="!isAdmin()"
          :can-edit="() => true"
          :can-delete="() => true"
          @retry="load"
          @delete="(image) => confirmDeleteImage(image, load)"
        >
          <template #empty-action>
          <Button type="primary" @click="openUpload">上传图片</Button>
          </template>
        </ImageGrid>
      </div>
    </Spin>

    <div v-if="total > 0" class="cp-pagination">
      <Pagination
        :current="readQuery().current"
        :page-size="readQuery().size"
        :total="total"
        show-less-items
        @change="changePage"
      />
    </div>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 28px;
}

.section-kicker {
  margin: 0 0 4px;
  color: var(--cp-text-soft);
  font-size: 10px;
  letter-spacing: 0.14em;
}

.page-head .cp-page-subtitle {
  margin-top: 4px;
}

.toolbar-count {
  margin-left: auto;
  color: var(--cp-text-soft);
  font-size: 13px;
}

.my-upload-grid :deep(.cp-masonry) {
  columns: 3;
  column-gap: 16px;
}

.my-upload-grid :deep(.card-with-status) {
  background: var(--cp-surface);
  border-color: var(--cp-border);
  border-radius: var(--cp-radius-lg);
}

.my-upload-grid :deep(.card-with-status .card-media) {
  border-radius: var(--cp-radius-lg) var(--cp-radius-lg) 0 0;
}

.my-upload-grid :deep(.card-with-status .card-body) {
  padding: 14px 16px 16px;
}

@media (max-width: 575px) {
  .page-head {
    flex-direction: column;
  }

  .page-head > .ant-btn {
    width: 100%;
  }

  .my-upload-grid :deep(.cp-masonry) {
    columns: 2;
    column-gap: 12px;
  }
}
</style>
