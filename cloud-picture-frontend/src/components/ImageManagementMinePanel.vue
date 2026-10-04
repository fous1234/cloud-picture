<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { Button } from 'ant-design-vue'
import { ReloadOutlined, SearchOutlined, TagsOutlined } from '@ant-design/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import type { Id, ImageVO } from '../api/types'
import { listMyImages } from '../api/image'
import { errorMessage } from '../api/http'
import { dataVersion, openEdit } from '../stores/ui'
import { confirmDeleteImage } from '../utils/imageActions'
import ImageManagementQueue from './ImageManagementQueue.vue'
import ImageManagementDetail from './ImageManagementDetail.vue'

defineProps<{
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
const selectedId = ref<Id | null>(null)
const nameInput = ref('')
const tagInput = ref('')
const detailHost = ref<HTMLElement | null>(null)
let requestSeq = 0

const route = useRoute()
const router = useRouter()

function routeQuery(key: string) {
  return route.query[key]
}

function readQuery() {
  const value = (input: unknown) => (typeof input === 'string' && input ? input : undefined)
  const current = Number(routeQuery('current')) > 0 ? Number(routeQuery('current')) : 1
  const size = Number(routeQuery('size')) > 0 ? Number(routeQuery('size')) : DEFAULT_PAGE_SIZE
  return {
    current,
    size,
    name: value(routeQuery('name')),
    tag: value(routeQuery('tag')),
  }
}

const query = computed(() => readQuery())
const selectedImage = computed(() => images.value.find((item) => item.id === selectedId.value) ?? null)
const queueLabel = computed(() => `${total.value} 张`)

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
    })
    if (seq !== requestSeq) return
    images.value = page.records ?? []
    total.value = page.total ?? 0
    if (!images.value.some((item) => item.id === selectedId.value)) {
      selectedId.value = images.value[0]?.id ?? null
    }
  } catch (e) {
    if (seq !== requestSeq) return
    images.value = []
    total.value = 0
    selectedId.value = null
    error.value = errorMessage(e)
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

function pushQuery(patch: Record<string, string | number | undefined>) {
  const merged = { ...query.value, ...patch }
  const next: Record<string, string | number> = { tab: 'mine' }
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

function resetSearch() {
  nameInput.value = ''
  tagInput.value = ''
  pushQuery({ name: undefined, tag: undefined, current: 1 })
}

function changePage(page: number, size: number) {
  pushQuery({ current: page, size })
}

function selectImage(id: Id) {
  selectedId.value = id
  if (window.innerWidth < 1024) {
    nextTick(() => detailHost.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
  }
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
    <div class="management-filter">
      <form class="management-search" @submit.prevent="submitSearch">
        <div class="management-search-field">
          <SearchOutlined />
          <input v-model="nameInput" type="text" placeholder="按标题检索..." />
        </div>
        <div class="management-search-field">
          <TagsOutlined />
          <input v-model="tagInput" type="text" placeholder="按标签检索..." />
        </div>
        <Button type="primary" class="management-search-submit" @click="submitSearch">搜索</Button>
        <button type="button" class="management-search-reset" title="清空检索" @click="resetSearch">
          <ReloadOutlined />
        </button>
      </form>
    </div>

    <div class="management-workbench">
      <ImageManagementQueue
        :images="images"
        mode="mine"
        :selected-id="selectedId"
        :loading="loading"
        :error="error"
        :total="total"
        :current="query.current"
        :size="query.size"
        :count-label="queueLabel"
        empty-description="还没有上传图片"
        show-upload-action
        @select="selectImage"
        @retry="load"
        @page="changePage"
        @upload="emit('upload')"
      />
      <div ref="detailHost" class="management-detail-column">
        <ImageManagementDetail
          :image="selectedImage"
          mode="mine"
          @edit="openEdit"
          @delete="(image) => confirmDeleteImage(image, load)"
        />
      </div>
    </div>
  </section>
</template>

<style scoped>
.management-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.management-filter {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-surface);
  box-shadow: var(--cp-shadow-subtle);
}

.management-search {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  min-width: 260px;
}

.management-search-field {
  position: relative;
  flex: 1 1 170px;
  max-width: 260px;
}

.management-search-field :deep(.anticon) {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--cp-text-muted);
  font-size: 15px;
  pointer-events: none;
}

.management-search-field input {
  width: 100%;
  height: 36px;
  padding: 0 12px 0 34px;
  border: 1px solid transparent;
  border-radius: var(--cp-radius);
  background: var(--cp-bg-soft);
  color: var(--cp-text);
  font: inherit;
  font-size: 12px;
}

.management-search-field input::placeholder {
  color: var(--cp-text-muted);
}

.management-search-field input:focus {
  outline: none;
  border-color: var(--cp-border);
  background: var(--cp-surface);
  box-shadow: 0 0 0 2px rgba(17, 24, 39, 0.06);
}

.management-search-submit {
  height: 36px;
  border-radius: var(--cp-radius);
}

.management-search-reset {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  flex: none;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius);
  background: var(--cp-surface);
  color: var(--cp-text-soft);
  cursor: pointer;
}

.management-search-reset:hover {
  background: var(--cp-bg-soft);
}

.management-workbench {
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 7fr);
  gap: 24px;
  align-items: start;
}

@media (max-width: 1023px) {
  .management-workbench {
    grid-template-columns: 1fr;
  }

  .management-search {
    min-width: 100%;
    justify-content: stretch;
  }

  .management-search-field {
    max-width: none;
  }
}
</style>