<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Button, Input, Pagination, RadioButton, RadioGroup, Skeleton } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import type { ImageVO, ReviewStatus } from '../api/types'
import { listMyImages } from '../api/image'
import { errorMessage } from '../api/http'
import { dataVersion } from '../stores/ui'
import { confirmDeleteImage } from '../utils/imageActions'
import ErrorState from './ErrorState.vue'
import EmptyState from './EmptyState.vue'
import ManagementImageCard from './ManagementImageCard.vue'

const props = defineProps<{
  stats: {
    total: number | null
    approved: number | null
    pending: number | null
    rejected: number | null
  }
}>()

const emit = defineEmits<{ upload: [] }>()

const DEFAULT_PAGE_SIZE = 12
const images = ref<ImageVO[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref<string | null>(null)
const nameInput = ref('')
const tagInput = ref('')
let requestSeq = 0

const STATUS_OPTIONS = [
  { value: 'all', label: '全部作品', key: 'total' as const },
  { value: '1', label: '审核通过 · 已入库', key: 'approved' as const },
  { value: '0', label: '审核中 · 待处理', key: 'pending' as const },
  { value: '2', label: '未通过审核', key: 'rejected' as const },
]

const route = useRoute()
const router = useRouter()

function readQuery() {
  const value = (input: unknown) => (typeof input === 'string' && input ? input : undefined)
  const current = Number(route.query.current) > 0 ? Number(route.query.current) : 1
  const size = Number(route.query.size) > 0 ? Number(route.query.size) : DEFAULT_PAGE_SIZE
  const statusValue = value(route.query.status)
  const status = statusValue && ['0', '1', '2'].includes(statusValue) ? statusValue : 'all'
  return {
    current,
    size,
    status,
    name: value(route.query.name),
    tag: value(route.query.tag),
  }
}

const query = computed(() => readQuery())
const status = computed({
  get: () => query.value.status,
  set: (value: string) => pushQuery({ status: value === 'all' ? undefined : value, current: 1 }),
})

function countFor(key: 'total' | 'approved' | 'pending' | 'rejected') {
  const value = props.stats[key]
  return value === null ? '—' : value
}

async function load() {
  const current = query.value
  const seq = ++requestSeq
  loading.value = true
  error.value = null
  try {
    const page = await listMyImages({
      current: current.current,
      size: current.size,
      name: current.name,
      tag: current.tag,
      reviewStatus:
        current.status === 'all' ? undefined : (Number(current.status) as ReviewStatus),
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

function pushQuery(patch: Record<string, string | number | undefined>) {
  const merged = { ...query.value, ...patch }
  const next: Record<string, string | number> = { tab: 'mine' }
  if (merged.status && merged.status !== 'all') next.status = merged.status
  if (merged.name) next.name = merged.name
  if (merged.tag) next.tag = merged.tag
  if (merged.current > 1) next.current = merged.current
  if (merged.size !== DEFAULT_PAGE_SIZE) next.size = merged.size
  router.push({ name: 'image-management', query: next })
}

function submitSearch() {
  pushQuery({
    name: nameInput.value.trim() || undefined,
    tag: tagInput.value.trim() || undefined,
    current: 1,
  })
}

function changePage(page: number, size: number) {
  pushQuery({ current: page, size })
}

watch(
  () => route.query,
  () => {
    nameInput.value = query.value.name ?? ''
    tagInput.value = query.value.tag ?? ''
    load()
  },
  { immediate: true },
)
watch(dataVersion, load)
</script>

<template>
  <section class="management-panel">
    <div class="management-toolbar">
      <RadioGroup v-model:value="status" button-style="solid">
        <RadioButton v-for="option in STATUS_OPTIONS" :key="option.value" :value="option.value">
          {{ option.label }}
          <span class="status-count">{{ countFor(option.key) }}</span>
        </RadioButton>
      </RadioGroup>
      <div class="management-search">
        <Input
          v-model:value="nameInput"
          allow-clear
          placeholder="搜索作品标题"
          @press-enter="submitSearch"
        />
        <Input
          v-model:value="tagInput"
          allow-clear
          placeholder="搜索标签"
          @press-enter="submitSearch"
        />
        <Button type="primary" @click="submitSearch">搜索</Button>
      </div>
    </div>

    <Skeleton v-if="loading && !images.length" :paragraph="{ rows: 7 }" active />
    <ErrorState v-else-if="error" message="我的上传加载失败" :description="error" @retry="load" />
    <EmptyState
      v-else-if="!images.length"
      :description="query.status === 'all' ? '还没有上传图片' : '该状态下没有图片'"
    >
      <Button type="primary" @click="emit('upload')">上传图片</Button>
    </EmptyState>

    <div v-else class="management-grid">
      <ManagementImageCard
        v-for="image in images"
        :key="image.id"
        mode="mine"
        :image="image"
        @delete="(item) => confirmDeleteImage(item, load)"
      />
    </div>

    <div v-if="loading && images.length" class="list-refreshing">正在刷新列表…</div>
    <Pagination
      v-if="total > 0"
      class="management-pagination"
      :current="query.current"
      :page-size="query.size"
      :total="total"
      show-less-items
      @change="changePage"
    />
  </section>
</template>

<style scoped>
.management-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.management-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border-radius: var(--cp-radius-lg);
  background: var(--cp-surface);
  box-shadow: 0 1px 4px rgba(17, 24, 39, 0.04);
}

.management-toolbar :deep(.ant-radio-button-wrapper) {
  border: 0;
  border-radius: 8px;
}

.status-count {
  margin-left: 4px;
  color: var(--cp-text-muted);
  font-size: 11px;
}

.management-toolbar :deep(.ant-radio-button-wrapper-checked) .status-count {
  color: inherit;
}

.management-search {
  display: flex;
  flex: 1;
  min-width: 280px;
  justify-content: flex-end;
  gap: 8px;
}

.management-search :deep(.ant-input) {
  width: 150px;
}

.management-grid {
  column-count: 3;
  column-gap: 24px;
}

.management-grid :deep(.management-card) {
  break-inside: avoid;
  margin-bottom: 24px;
}

.list-refreshing {
  color: var(--cp-text-soft);
  font-size: 12px;
  text-align: center;
}

.management-pagination {
  align-self: center;
  margin-top: 8px;
}

@media (max-width: 991px) {
  .management-grid {
    column-count: 2;
  }
}

@media (max-width: 767px) {
  .management-toolbar {
    align-items: stretch;
  }

  .management-search {
    min-width: 100%;
    justify-content: stretch;
  }

  .management-search :deep(.ant-input) {
    flex: 1;
    width: auto;
  }
}

@media (max-width: 575px) {
  .management-grid {
    column-count: 1;
    column-gap: 14px;
  }

  .management-grid :deep(.management-card) {
    margin-bottom: 14px;
  }
}
</style>
