<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Button, Input, Pagination, Select, Spin, Tag } from 'ant-design-vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import type { ImageVO } from '../api/types'
import { listImages, listTags } from '../api/image'
import { errorMessage } from '../api/http'
import ImageGrid from '../components/ImageGrid.vue'
import { isAdmin } from '../stores/session'
import { CATEGORY_OPTIONS, dataVersion, openUpload } from '../stores/ui'
import { confirmDeleteImage, isOwner } from '../utils/imageActions'

const route = useRoute()
const router = useRouter()

const DEFAULT_PAGE_SIZE = 12

const images = ref<ImageVO[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref<string | null>(null)
const hotTags = ref<string[]>([])
const tagsLoading = ref(false)
const tagsFailed = ref(false)
const keyword = ref('')

/** 防止乱序响应覆盖较新的搜索结果 */
let requestSeq = 0

function readQuery() {
  const query = route.query
  const asString = (value: unknown) => (typeof value === 'string' && value ? value : undefined)
  const asNumber = (value: unknown, fallback: number) => {
    const parsed = Number(value)
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
  }
  return {
    q: asString(query.q),
    category: asString(query.category),
    tag: asString(query.tag),
    current: asNumber(query.current, 1),
    size: asNumber(query.size, DEFAULT_PAGE_SIZE),
  }
}

const filters = computed(() => readQuery())
const hasFilters = computed(() => !!(filters.value.q || filters.value.category || filters.value.tag))

async function loadImages() {
  const { q, category, tag, current, size } = readQuery()
  const seq = ++requestSeq
  loading.value = true
  error.value = null
  try {
    const page = await listImages({
      current,
      size,
      name: q,
      category,
      tag,
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

async function loadTags() {
  tagsLoading.value = true
  tagsFailed.value = false
  try {
    hotTags.value = (await listTags(20)) ?? []
  } catch {
    // 标签区失败不阻断图库，仅隐藏该区域并提供局部重试
    tagsFailed.value = true
  } finally {
    tagsLoading.value = false
  }
}

watch(() => route.query, loadImages, { immediate: true })
watch(dataVersion, loadImages)
watch(
  () => route.query.q,
  (value) => {
    keyword.value = typeof value === 'string' ? value : ''
  },
  { immediate: true },
)
loadTags()

// 输入停止后短暂防抖请求，回车立即提交
let debounceTimer: number | undefined
watch(keyword, (value) => {
  window.clearTimeout(debounceTimer)
  debounceTimer = window.setTimeout(() => {
    const next = value.trim() || undefined
    if (next === filters.value.q) return
    applyQuery({ q: next, current: 1 })
  }, 400)
})

/** 把筛选状态写入 URL（默认值不写入，保持地址可读） */
function applyQuery(patch: Record<string, string | number | undefined>) {
  const merged = { ...readQuery(), ...patch }
  const query: Record<string, string | number> = {}
  if (merged.q) query.q = merged.q
  if (merged.category) query.category = merged.category
  if (merged.tag) query.tag = merged.tag
  if (merged.current > 1) query.current = merged.current
  if (merged.size !== DEFAULT_PAGE_SIZE) query.size = merged.size
  router.push({ name: 'gallery', query })
}

function submitSearch() {
  applyQuery({ q: keyword.value.trim() || undefined, current: 1 })
}

function setCategory(value: unknown) {
  applyQuery({ category: (value as string) || undefined, current: 1 })
}

function toggleTag(tag: string) {
  applyQuery({ tag: filters.value.tag === tag ? undefined : tag, current: 1 })
}

function clearFilters() {
  keyword.value = ''
  router.push({ name: 'gallery' })
}

function changePage(page: number, size: number) {
  applyQuery({ current: page, size })
}

function usable(image: ImageVO) {
  return isOwner(image) || isAdmin()
}

function onDelete(image: ImageVO) {
  confirmDeleteImage(image, loadImages)
}

const emptyText = computed(() =>
  hasFilters.value ? '没有找到匹配图片' : '图库还没有图片',
)
</script>

<template>
  <div class="gallery-view">
    <section v-if="!hasFilters" class="hero">
      <div class="cp-container hero-inner">
        <div class="hero-copy">
          <p class="hero-kicker">EDITORIAL CURATION <span>·</span> 灵感收录</p>
          <h1 class="hero-title">发现灵感，收藏每一刻</h1>
          <p class="hero-subtitle">
            浏览团队共享的高质量摄影图片与创意灵感，支持按分类与标签快速检索。
          </p>
          <div class="hero-search-row">
            <Select
              :value="filters.category"
              :options="CATEGORY_OPTIONS"
              placeholder="全部分类"
              class="hero-category"
              allow-clear
              @change="setCategory"
            />
        <Input
          v-model:value="keyword"
          size="large"
          class="hero-search"
          placeholder="搜索图片标题或标签..."
          allow-clear
          @press-enter="submitSearch"
        >
          <template #prefix><SearchOutlined /></template>
          <template #suffix>
            <Button type="primary" @click="submitSearch">
              <template #icon><SearchOutlined /></template>
              搜索
            </Button>
          </template>
        </Input>
          </div>
          <div v-if="hotTags.length" class="hero-hot-search">
            <span>热门搜索：</span>
            <button v-for="tag in hotTags.slice(0, 4)" :key="tag" type="button" @click="toggleTag(tag)">
              #{{ tag }}
            </button>
          </div>
        </div>

        <div class="hero-art" aria-hidden="true">
          <img class="hero-photo hero-photo-main" src="/assets/hero-mist.jpg" alt="" />
          <img class="hero-photo hero-photo-campus" src="/assets/hero-campus.jpg" alt="" />
          <img class="hero-photo hero-photo-library" src="/assets/hero-library.jpg" alt="" />
          <div class="hero-art-note">
            <span class="hero-art-dot"></span>
            <span>校园影像 · 灵感精选</span>
          </div>
        </div>
      </div>
    </section>

    <main class="cp-container cp-page gallery-content">
      <div v-if="hasFilters" class="search-results-head">
        <p class="section-kicker">SEARCH RESULTS</p>
        <h1 class="cp-page-title">“{{ filters.q || filters.category || filters.tag }}” 的搜索结果</h1>
        <p class="cp-page-subtitle">浏览符合条件的共享图片</p>
      </div>

      <div v-else class="gallery-section-head">
        <div>
          <p class="section-kicker">CAMPUS PHOTO COLLECTION</p>
          <h2>共享图库</h2>
        </div>
        <span class="toolbar-count">共 {{ total }} 张图片</span>
      </div>

      <div v-if="!hasFilters" class="category-strip" aria-label="图片分类">
        <button class="category-chip category-chip-active" type="button" @click="clearFilters">
          全部作品
        </button>
        <button
          v-for="option in CATEGORY_OPTIONS"
          :key="String(option.value)"
          class="category-chip"
          type="button"
          @click="setCategory(option.value)"
        >
          {{ option.label }}
        </button>
      </div>

      <div v-if="hasFilters" class="cp-toolbar search-toolbar">
        <Select
          :value="filters.category"
          :options="CATEGORY_OPTIONS"
          placeholder="全部分类"
          style="min-width: 150px"
          allow-clear
          @change="setCategory"
        />
        <Button v-if="hasFilters" type="link" @click="clearFilters">清除筛选</Button>
        <span class="toolbar-count">共 {{ total }} 张图片</span>
      </div>

      <div v-if="hasFilters" class="cp-tag-row">
        <Tag v-if="filters.q" closable @close="applyQuery({ q: undefined, current: 1 })">
          关键词：{{ filters.q }}
        </Tag>
        <Tag v-if="filters.category" closable @close="setCategory(undefined)">
          分类：{{ filters.category }}
        </Tag>
        <Tag v-if="filters.tag" closable @close="toggleTag(filters.tag)">
          标签：{{ filters.tag }}
        </Tag>
      </div>

      <div v-if="hasFilters" class="tags-block">
        <div class="tags-label">常用标签</div>
        <div v-if="tagsLoading" class="tags-loading">
          <Spin size="small" />
        </div>
        <div v-else-if="tagsFailed" class="tags-failed">
          标签加载失败
          <Button type="link" size="small" @click="loadTags">重试</Button>
        </div>
        <div v-else-if="!hotTags.length" class="tags-failed">还没有常用标签</div>
        <div v-else class="cp-tag-row tags-list">
          <Tag
            v-for="tag in hotTags"
            :key="tag"
            class="tag-chip"
            :class="{ 'tag-chip-active': filters.tag === tag }"
            @click="toggleTag(tag)"
          >
            {{ tag }}
          </Tag>
        </div>
      </div>

      <Spin :spinning="loading && images.length > 0">
        <ImageGrid
          :loading="loading && !images.length"
          :error="error"
          :images="images"
          :empty-text="emptyText"
          :can-edit="usable"
          :can-delete="usable"
          :can-review="() => isAdmin()"
          @retry="loadImages"
          @delete="onDelete"
        >
          <template #empty-action>
            <Button v-if="!hasFilters" type="primary" @click="openUpload">上传图片</Button>
            <Button v-else @click="clearFilters">清除筛选</Button>
          </template>
        </ImageGrid>
      </Spin>

      <div v-if="total > 0" class="cp-pagination">
        <Pagination
          :current="filters.current"
          :page-size="filters.size"
          :total="total"
          :page-size-options="['12', '24', '48']"
          show-size-changer
          show-less-items
          @change="changePage"
        />
      </div>
    </main>
  </div>
</template>

<style scoped>
.hero {
  overflow: hidden;
  background: #f0f2f4;
}

.hero-inner {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 0.92fr);
  align-items: center;
  gap: 40px;
  min-height: 390px;
  padding-top: 36px;
  padding-bottom: 36px;
}

.hero-copy {
  position: relative;
  z-index: 1;
  max-width: 620px;
}

.hero-kicker,
.section-kicker {
  margin: 0 0 12px;
  color: #616a72;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.hero-kicker span {
  padding: 0 5px;
  color: #a1a6ac;
}

.hero-title {
  max-width: 610px;
  margin: 0;
  font-size: clamp(36px, 4vw, 54px);
  font-weight: 650;
  line-height: 1.18;
  letter-spacing: -0.045em;
}

.hero-subtitle {
  max-width: 540px;
  margin: 14px 0 24px;
  color: var(--cp-text-soft);
}

.hero-search {
  flex: 1;
  min-width: 0;
}

.hero-search-row {
  display: flex;
  gap: 8px;
  max-width: 620px;
  padding: 7px;
  border: 1px solid #e1e3e6;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 8px 28px rgba(20, 27, 36, 0.06);
}

.hero-category {
  width: 145px;
  flex: 0 0 145px;
}

@media (min-width: 768px) {
  .hero-search-row {
    align-items: center;
  }

  .hero-category :deep(.ant-select-selector),
  .hero-search :deep(.ant-input-affix-wrapper) {
    height: 50px;
    box-sizing: border-box;
  }

  .hero-category :deep(.ant-select-selection-item),
  .hero-category :deep(.ant-select-selection-placeholder) {
    line-height: 48px;
  }

  .hero-search :deep(.ant-input-suffix .ant-btn) {
    height: 40px;
    display: inline-flex;
    align-items: center;
  }
}

@media (min-width: 768px) and (max-width: 1023px) {
  .hero-inner {
    grid-template-columns: 1fr;
    gap: 20px;
    min-height: auto;
    padding-top: 30px;
    padding-bottom: 28px;
  }

  .hero-art {
    display: none;
  }

  .hero-search-row {
    width: 100%;
    max-width: 760px;
    flex-wrap: wrap;
  }

  .hero-category,
  .hero-search {
    width: 100%;
    flex-basis: 100%;
  }
}

.hero-search :deep(.ant-input-affix-wrapper) {
  border: 0;
  box-shadow: none;
}

.hero-search :deep(.ant-input-suffix) .ant-btn {
  border-radius: 8px;
}

.hero-hot-search {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 14px;
  color: var(--cp-text-soft);
  font-size: 12px;
}

.hero-hot-search button {
  padding: 0;
  border: 0;
  background: none;
  color: #515963;
  cursor: pointer;
  font: inherit;
}

.hero-art {
  position: relative;
  min-height: 305px;
}

.hero-photo {
  position: absolute;
  display: block;
  border: 5px solid #fff;
  border-radius: 10px;
  box-shadow: 0 18px 42px rgba(26, 35, 43, 0.18);
  object-fit: cover;
}

.hero-photo-main {
  top: 0;
  right: 19%;
  width: 47%;
  height: 272px;
  transform: rotate(-4deg);
}

.hero-photo-campus {
  right: 52%;
  bottom: 4px;
  width: 38%;
  height: 154px;
  transform: rotate(4deg);
}

.hero-photo-library {
  right: 2%;
  bottom: 5px;
  width: 42%;
  height: 148px;
  transform: rotate(3deg);
}

.hero-art-note {
  position: absolute;
  right: 8%;
  bottom: 18px;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 12px;
  border: 1px solid rgba(255, 255, 255, 0.75);
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 8px 20px rgba(26, 35, 43, 0.12);
  color: #41484f;
  font-size: 11px;
}

.hero-art-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #678977;
}

.gallery-content {
  padding-top: 30px;
  padding-bottom: 72px;
}

.gallery-section-head {
  display: flex;
  align-items: end;
  justify-content: space-between;
  margin-bottom: 16px;
}

.gallery-section-head h2 {
  margin: 0;
  font-size: 23px;
  font-weight: 650;
}

.gallery-section-head .section-kicker {
  margin-bottom: 4px;
}

.search-results-head {
  margin-bottom: 20px;
}

.search-results-head .cp-page-title {
  margin-bottom: 4px;
  font-size: 26px;
}

.category-strip {
  display: flex;
  gap: 9px;
  margin: 0 0 22px;
  overflow-x: auto;
  padding-bottom: 4px;
  scrollbar-width: none;
}

.category-strip::-webkit-scrollbar {
  display: none;
}

.category-chip {
  flex: 0 0 auto;
  padding: 8px 14px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: #eceef1;
  color: #535b64;
  cursor: pointer;
  font: inherit;
  font-size: 12px;
}

.category-chip-active {
  background: var(--cp-accent);
  color: #fff;
}

.toolbar-count {
  margin-left: auto;
  color: var(--cp-text-soft);
  font-size: 13px;
}

.tags-block {
  margin-bottom: 8px;
}

.tags-label {
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--cp-text-soft);
}

.tags-list {
  flex-wrap: nowrap;
  overflow-x: auto;
  padding-bottom: 4px;
}

.tag-chip {
  cursor: pointer;
  user-select: none;
  background: var(--cp-bg-soft);
  border-color: var(--cp-border);
}

.tag-chip-active {
  background: var(--cp-accent);
  border-color: var(--cp-accent);
  color: #fff;
}

.tags-loading,
.tags-failed {
  margin-bottom: 20px;
  color: var(--cp-text-soft);
  font-size: 13px;
}

@media (max-width: 767px) {
  .hero-inner {
    grid-template-columns: 1fr;
    gap: 12px;
    min-height: 0;
    padding-top: 30px;
    padding-bottom: 22px;
  }

  .hero-title {
    max-width: 390px;
    font-size: 30px;
  }

  .hero-subtitle {
    margin: 8px 0 18px;
    font-size: 12px;
  }

  .hero-art {
    min-height: 210px;
    margin-top: 8px;
  }

  .hero-photo-main {
    right: 24%;
    width: 44%;
    height: 188px;
  }

  .hero-photo-campus {
    right: 55%;
    width: 34%;
    height: 112px;
  }

  .hero-photo-library {
    right: 4%;
    width: 38%;
    height: 110px;
  }

  .hero-art-note {
    right: 3%;
    bottom: 0;
    font-size: 10px;
  }

  .gallery-content {
    padding-top: 20px;
  }

  .gallery-section-head h2 {
    font-size: 20px;
  }

  .search-results-head .cp-page-title {
    font-size: 22px;
  }

  .hero-category {
    width: 112px;
    flex-basis: 112px;
  }

  .hero-search-row {
    gap: 2px;
    padding: 5px;
  }

  .hero-search :deep(.ant-input-suffix .ant-btn) {
    padding-inline: 8px;
  }

  .toolbar-count {
    margin-left: 0;
  }
}

@media (max-width: 575px) {
  .hero-art {
    display: none;
  }

  .hero-inner {
    padding-top: 24px;
  }

  .hero-title {
    font-size: 27px;
  }

  .hero-search-row {
    flex-wrap: wrap;
  }

  .hero-category {
    width: 100%;
    flex-basis: 100%;
  }

  .hero-search {
    flex-basis: 100%;
  }

  .gallery-section-head {
    align-items: flex-start;
  }

  .gallery-section-head .toolbar-count {
    font-size: 11px;
  }
}
</style>
