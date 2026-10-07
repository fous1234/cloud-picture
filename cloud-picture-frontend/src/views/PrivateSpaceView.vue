<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { Button, Input, Modal, Pagination, Select, Skeleton, message } from 'ant-design-vue'
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import type { RouteLocationRaw } from 'vue-router'
import type { Id, SpaceImageQuery, SpaceImageVO, SpaceVO } from '../api/types'
import {
  createSpace,
  downloadSpaceImage,
  getMySpace,
  listSpaceImages,
  renameSpace,
} from '../api/space'
import { errorMessage } from '../api/http'
import { CATEGORY_OPTIONS } from '../stores/ui'
import { formatSize } from '../utils/format'
import { confirmDeleteSpace, confirmDeleteSpaceImage } from '../utils/spaceActions'
import AppBreadcrumb from '../components/AppBreadcrumb.vue'
import EmptyState from '../components/EmptyState.vue'
import ErrorState from '../components/ErrorState.vue'
import SpaceImageCard from '../components/SpaceImageCard.vue'
import SpaceImageDetailPanel from '../components/SpaceImageDetailPanel.vue'
import SpaceImagePreviewModal from '../components/SpaceImagePreviewModal.vue'
import SpaceUploadModal from '../components/SpaceUploadModal.vue'

const DEFAULT_PAGE_SIZE = 12

const route = useRoute()
const router = useRouter()

const space = ref<SpaceVO | null>(null)
const spaceLoading = ref(true)
const spaceError = ref<string | null>(null)

const images = ref<SpaceImageVO[]>([])
const total = ref(0)
const listLoading = ref(false)
const listError = ref<string | null>(null)
const selectedId = ref<Id | null>(null)

const guideName = ref('')
const creating = ref(false)
const uploadOpen = ref(false)
const downloading = ref(false)

/** 大图预览：自建受控 Modal（用原图），关闭即从 DOM 移除，不依赖 ant 预览层的内部状态 */
const previewImage = ref<SpaceImageVO | null>(null)
const previewOpen = ref(false)

function openPreview(image: SpaceImageVO) {
  if (!image.url) return
  previewImage.value = image
  previewOpen.value = true
}

const renameOpen = ref(false)
const renameName = ref('')
const renaming = ref(false)

const nameInput = ref('')
const tagInput = ref('')
const detailHost = ref<HTMLElement | null>(null)

let requestSeq = 0

const crumbs: { label: string; to?: RouteLocationRaw }[] = [
  { label: '首页', to: { name: 'gallery' } },
  { label: '私有空间' },
]

function readQuery(): SpaceImageQuery {
  const asString = (value: unknown) => (typeof value === 'string' && value ? value : undefined)
  const current = Number(route.query.current) > 0 ? Number(route.query.current) : 1
  const size = Number(route.query.size) > 0 ? Number(route.query.size) : DEFAULT_PAGE_SIZE
  return {
    current,
    size,
    name: asString(route.query.name),
    category: asString(route.query.category),
    tag: asString(route.query.tag),
  }
}

const query = computed(() => readQuery())
const selectedImage = computed(
  () => images.value.find((image) => image.id === selectedId.value) ?? null,
)

async function loadSpace() {
  spaceLoading.value = true
  spaceError.value = null
  try {
    space.value = await getMySpace()
  } catch (e) {
    space.value = null
    spaceError.value = errorMessage(e)
  } finally {
    spaceLoading.value = false
  }
}

async function loadList() {
  if (!space.value) return
  const current = query.value
  const seq = ++requestSeq
  listLoading.value = true
  listError.value = null
  try {
    const page = await listSpaceImages(current)
    if (seq !== requestSeq) return
    images.value = page.records ?? []
    total.value = page.total ?? 0
    if (!images.value.some((image) => image.id === selectedId.value)) {
      selectedId.value = images.value[0]?.id ?? null
    }
  } catch (e) {
    if (seq !== requestSeq) return
    images.value = []
    total.value = 0
    selectedId.value = null
    listError.value = errorMessage(e)
  } finally {
    if (seq === requestSeq) listLoading.value = false
  }
}

async function loadAll() {
  await loadSpace()
  await loadList()
}

function pushQuery(patch: Partial<SpaceImageQuery>) {
  const merged = { ...query.value, ...patch }
  const next: Record<string, string | number> = {}
  if (merged.name) next.name = merged.name
  if (merged.category) next.category = merged.category
  if (merged.tag) next.tag = merged.tag
  if (merged.current > 1) next.current = merged.current
  if (merged.size !== DEFAULT_PAGE_SIZE) next.size = merged.size
  router.push({ name: 'private-space', query: next })
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

function changeCategory(value: unknown) {
  pushQuery({ category: (value as string) || undefined, current: 1 })
}

function changePage(page: number, size: number) {
  pushQuery({ current: page, size })
}

async function onCreate() {
  if (creating.value) return
  creating.value = true
  try {
    const created = await createSpace(guideName.value.trim() || undefined)
    space.value = created
    guideName.value = ''
    message.success('私有空间已创建')
    await loadList()
  } catch (e) {
    message.error(errorMessage(e))
    // 已被其它标签页创建时同步一次真实状态
    await loadSpace()
    await loadList()
  } finally {
    creating.value = false
  }
}

function openRename() {
  if (!space.value) return
  renameName.value = space.value.name
  renameOpen.value = true
}

async function onRename() {
  const name = renameName.value.trim()
  if (!name) {
    message.warning('空间名称不能为空')
    return
  }
  if (renaming.value) return
  renaming.value = true
  try {
    await renameSpace(name)
    if (space.value) space.value = { ...space.value, name }
    renameOpen.value = false
    message.success('空间名称已更新')
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    renaming.value = false
  }
}

function onDeleteSpace() {
  if (!space.value) return
  confirmDeleteSpace(space.value.imageCount, async () => {
    space.value = null
    images.value = []
    total.value = 0
    selectedId.value = null
    await loadSpace()
  })
}

async function onDownload(image: SpaceImageVO) {
  if (downloading.value) return
  downloading.value = true
  try {
    const url = await downloadSpaceImage(image.id)
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

function onDeleteImage(image: SpaceImageVO) {
  confirmDeleteSpaceImage(image, loadAll)
}

function selectImage(id: Id) {
  selectedId.value = id
  if (window.innerWidth < 1024) {
    nextTick(() => detailHost.value?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
  }
}

// 瀑布流列数随窗口宽度变化：窄屏 1 列、中等 2 列、宽屏 3 列
const columnCount = ref(3)

function updateColumnCount() {
  const width = window.innerWidth
  columnCount.value = width < 640 ? 1 : width < 1280 ? 2 : 3
}

const imageColumns = computed(() => {
  const columns: SpaceImageVO[][] = Array.from({ length: columnCount.value }, () => [])
  images.value.forEach((image, index) => columns[index % columnCount.value].push(image))
  return columns
})

onMounted(() => {
  updateColumnCount()
  window.addEventListener('resize', updateColumnCount)
})

onUnmounted(() => window.removeEventListener('resize', updateColumnCount))

watch(
  () => route.query,
  () => {
    nameInput.value = query.value.name ?? ''
    tagInput.value = query.value.tag ?? ''
    loadList()
  },
)

loadSpace().then(loadList)
</script>

<template>
  <div class="cp-container cp-page private-space-page">
    <AppBreadcrumb :items="crumbs" />

    <Skeleton v-if="spaceLoading" class="space-skeleton" :paragraph="{ rows: 6 }" active />

    <ErrorState
      v-else-if="spaceError"
      message="私有空间加载失败"
      :description="spaceError"
      @retry="loadAll"
    />

    <!-- 未创建：引导创建态 -->
    <section v-else-if="!space" class="space-guide">
      <EmptyState description="创建一个只有自己能看到的图库">
        <div class="space-guide-form">
          <Input
            v-model:value="guideName"
            :maxlength="64"
            placeholder="我的私有空间"
            @press-enter="onCreate"
          />
          <Button type="primary" :loading="creating" @click="onCreate">创建我的私有空间</Button>
        </div>
      </EmptyState>
    </section>

    <template v-else>
      <!-- 空间头部 -->
      <section class="space-head">
        <div class="space-head-main">
          <div class="space-head-icon" aria-hidden="true">
            <svg fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path d="M4 20h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.93a2 2 0 0 1-1.66-.9l-.82-1.2A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13c0 1.1.9 2 2 2Z"></path>
              <path d="M12 10v6"></path>
              <path d="m9 13 3-3 3 3"></path>
            </svg>
          </div>
          <div class="space-head-copy">
            <div class="space-head-title-row">
              <h1 class="cp-page-title space-head-title" :title="space.name">{{ space.name }}</h1>
              <button class="space-rename" type="button" title="修改空间名称" @click="openRename">
                改名
              </button>
            </div>
            <div class="space-head-meta">
              <span class="space-stat">
                <span class="space-stat-dot"></span>
                共 {{ space.imageCount }} 张 · 占用 {{ formatSize(space.totalSize) }}
              </span>
              <button class="space-delete" type="button" @click="onDeleteSpace">删除空间</button>
            </div>
          </div>
        </div>
        <Button type="primary" class="space-upload-button" @click="uploadOpen = true">
          上传到私有空间
        </Button>
      </section>

      <!-- 隐私说明 -->
      <div class="space-privacy">
        <p>
          <strong>仅你本人可见：</strong>
          私有图片免审核，不会出现在共享图库，系统管理员也无权限查验与提取图片内容。
        </p>
      </div>

      <!-- 筛选工具条 -->
      <section class="space-toolbar">
        <form class="space-toolbar-inputs" @submit.prevent="submitSearch">
          <div class="space-field space-field-name">
            <SearchOutlined />
            <input v-model="nameInput" type="text" placeholder="搜索私有图片名称..." />
          </div>
          <Select
            :value="query.category"
            :options="CATEGORY_OPTIONS"
            placeholder="全部类别"
            class="space-category"
            allow-clear
            @change="changeCategory"
          />
          <div class="space-field space-field-tag">
            <span class="space-field-hash">#</span>
            <input v-model="tagInput" type="text" placeholder="输入标签，如 胶片" />
          </div>
          <Button type="primary" class="space-search-button" @click="submitSearch">搜索</Button>
          <button class="space-reset" type="button" title="重置筛选" @click="resetSearch">
            <ReloadOutlined />
          </button>
        </form>
        <span class="space-result-count">
          当前筛选结果 <strong>{{ total }}</strong> 幅作品
        </span>
      </section>

      <!-- 网格 + 详情 -->
      <section class="space-workspace">
        <div class="space-grid-column">
          <div v-if="listLoading && !images.length" class="cp-masonry space-masonry-2">
            <div v-for="index in columnCount" :key="index" class="cp-masonry-column">
              <Skeleton.Image v-for="row in 2" :key="row" class="space-skeleton-card" active />
            </div>
          </div>

          <ErrorState
            v-else-if="listError"
            message="私有图片加载失败"
            :description="listError"
            @retry="loadList"
          />

          <EmptyState v-else-if="!images.length" description="私有空间还没有图片">
            <Button type="primary" @click="uploadOpen = true">上传到私有空间</Button>
          </EmptyState>

          <div v-else class="cp-masonry" :class="`space-masonry-${columnCount}`">
            <div
              v-for="(column, columnIndex) in imageColumns"
              :key="columnIndex"
              class="cp-masonry-column"
            >
              <SpaceImageCard
                v-for="image in column"
                :key="image.id"
                :image="image"
                :selected="image.id === selectedId"
                @select="selectImage(image.id)"
                @download="onDownload(image)"
                @delete="onDeleteImage(image)"
                @open="openPreview(image)"
              />
            </div>
          </div>

          <div v-if="total > 0" class="cp-pagination">
            <Pagination
              :current="query.current"
              :page-size="query.size"
              :total="total"
              show-less-items
              @change="changePage"
            />
          </div>
        </div>

        <div ref="detailHost" class="space-detail-column">
          <SpaceImageDetailPanel
            :image="selectedImage"
            @download="selectedImage && onDownload(selectedImage)"
            @delete="selectedImage && onDeleteImage(selectedImage)"
            @open="selectedImage && openPreview(selectedImage)"
          />
        </div>
      </section>
    </template>

    <SpaceUploadModal v-model:open="uploadOpen" @uploaded="loadAll" />

    <Modal
      v-model:open="renameOpen"
      title="修改空间名称"
      :confirm-loading="renaming"
      ok-text="保存"
      cancel-text="取消"
      @ok="onRename"
    >
      <Input v-model:value="renameName" :maxlength="64" placeholder="输入空间名称" @press-enter="onRename" />
    </Modal>

    <!-- 大图预览：深色画布 + 缩放/旋转，关闭即销毁内容，页面里不会残留任何图片节点 -->
    <SpaceImagePreviewModal v-model:open="previewOpen" :image="previewImage" />
  </div>
</template>

<style scoped>
.private-space-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.space-skeleton {
  margin-top: 24px;
}

.space-skeleton-card {
  width: 100%;
  height: 180px;
}

.space-skeleton-card :deep(.ant-skeleton-image) {
  width: 100%;
  height: 100%;
}

/* 引导创建态 */
.space-guide {
  margin-top: 16px;
}

.space-guide-form {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  max-width: 480px;
  margin: 16px auto 0;
}

/* 空间头部 */
.space-head {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 24px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-surface);
  box-shadow: var(--cp-shadow-subtle);
}

.space-head-main {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.space-head-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  flex: none;
  border-radius: var(--cp-radius);
  background: var(--cp-accent);
  color: #fff;
}

.space-head-icon svg {
  width: 24px;
  height: 24px;
}

.space-head-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
}

.space-head-title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.space-head-title {
  overflow: hidden;
  margin: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.space-rename {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--cp-text-soft);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  font-weight: 500;
}

.space-rename:hover {
  border-color: var(--cp-border);
  background: var(--cp-bg-soft);
  color: var(--cp-text);
}

.space-head-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.space-stat {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 10px;
  border-radius: 999px;
  background: var(--cp-bg);
  color: var(--cp-text-soft);
  font-size: 12px;
  font-weight: 500;
}

.space-stat-dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: #10b981;
}

.space-delete {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #cf1322;
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  font-weight: 500;
}

.space-delete:hover {
  background: var(--cp-status-rejected-bg);
}

.space-upload-button {
  align-self: stretch;
  height: 40px;
  border-radius: var(--cp-radius);
  font-weight: 600;
}

/* 隐私说明 */
.space-privacy {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border: 1px solid var(--cp-status-approved-border);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-status-approved-bg);
}

.space-privacy p {
  margin: 0;
  color: var(--cp-status-approved-fg);
  font-size: 13px;
  line-height: 1.6;
}

/* 筛选工具条 */
.space-toolbar {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-surface);
  box-shadow: var(--cp-shadow-subtle);
}

.space-toolbar-inputs {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.space-field {
  position: relative;
  flex: 1 1 180px;
  min-width: 0;
}

.space-field :deep(.anticon) {
  position: absolute;
  top: 50%;
  left: 12px;
  color: var(--cp-text-muted);
  font-size: 15px;
  pointer-events: none;
  transform: translateY(-50%);
}

.space-field input {
  width: 100%;
  height: 36px;
  padding: 0 12px 0 34px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius);
  background: var(--cp-bg-soft);
  color: var(--cp-text);
  font: inherit;
  font-size: 12px;
}

.space-field input::placeholder {
  color: var(--cp-text-muted);
}

.space-field input:focus {
  outline: none;
  border-color: var(--cp-accent);
  background: var(--cp-surface);
}

.space-field-tag {
  flex: 0 1 200px;
}

.space-field-hash {
  position: absolute;
  top: 50%;
  left: 12px;
  color: var(--cp-text-muted);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
  font-weight: 600;
  transform: translateY(-50%);
}

.space-field-tag input {
  padding-left: 26px;
}

.space-category {
  width: 150px;
  flex: none;
}

.space-category :deep(.ant-select-selector) {
  height: 36px !important;
  border-color: var(--cp-border) !important;
  border-radius: var(--cp-radius) !important;
  background: var(--cp-bg-soft) !important;
  font-size: 12px;
}

.space-category :deep(.ant-select-selection-item),
.space-category :deep(.ant-select-selection-placeholder) {
  line-height: 34px;
}

.space-search-button {
  height: 36px;
  border-radius: var(--cp-radius);
}

.space-reset {
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

.space-reset:hover {
  background: var(--cp-bg-soft);
  color: var(--cp-text);
}

.space-result-count {
  flex: none;
  color: var(--cp-text-soft);
  font-size: 12px;
}

.space-result-count strong {
  color: var(--cp-text);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-weight: 600;
}

/* 工作区：宽屏 68% 网格 + 32% 详情 */
.space-workspace {
  display: grid;
  grid-template-columns: minmax(0, 68fr) minmax(0, 32fr);
  align-items: start;
  gap: 24px;
}

.space-grid-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.space-detail-column {
  position: sticky;
  top: calc(var(--cp-header-height) + 16px);
}

.space-masonry-1 {
  --cp-masonry-columns: 1;
}

.space-masonry-2 {
  --cp-masonry-columns: 2;
}

.space-masonry-3 {
  --cp-masonry-columns: 3;
}

@media (min-width: 576px) {
  .space-head {
    flex-direction: row;
    align-items: center;
  }

  .space-upload-button {
    align-self: center;
  }
}

@media (min-width: 1024px) {
  .space-toolbar {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }
}

/* 窄屏：详情面板堆叠到网格下方 */
@media (max-width: 1023px) {
  .space-workspace {
    grid-template-columns: 1fr;
  }

  .space-detail-column {
    position: static;
    top: auto;
  }
}

@media (max-width: 575px) {
  .space-toolbar-inputs {
    flex-direction: column;
    align-items: stretch;
  }

  .space-field,
  .space-category,
  .space-field-tag {
    width: 100%;
    flex: 1 1 auto;
  }

  .space-search-button,
  .space-reset {
    width: 100%;
  }
}
</style>