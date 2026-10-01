<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Alert,
  Button,
  Form,
  FormItem,
  Input,
  InputNumber,
  Modal,
  Pagination,
  RadioButton,
  RadioGroup,
  Skeleton,
  Tag,
  message,
} from 'ant-design-vue'
import { DeleteOutlined, SafetyCertificateOutlined } from '@ant-design/icons-vue'
import type { ImageVO, ReviewStatus } from '../api/types'
import { listAdminImages } from '../api/admin-image'
import { importPexelsImages } from '../api/pexels-import'
import type { ImportResult } from '../api/pexels-import'
import { ApiError, errorMessage } from '../api/http'
import ErrorState from '../components/ErrorState.vue'
import EmptyState from '../components/EmptyState.vue'
import { dataVersion, openReview, REVIEW_STATUS_COLOR, REVIEW_STATUS_TEXT } from '../stores/ui'
import { isAdmin } from '../stores/session'
import { confirmDeleteImage } from '../utils/imageActions'
import { formatDateTime, formatDimensions } from '../utils/format'
import { normalizeTags, tagsTooLong } from '../utils/tags'

const route = useRoute()
const router = useRouter()

const DEFAULT_PAGE_SIZE = 10

const images = ref<ImageVO[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref<string | null>(null)
const nameInput = ref('')
const tagInput = ref('')
const selectedImageId = ref<string | null>(null)
const pexelsImportOpen = ref(false)
const pexelsImportSubmitting = ref(false)
const pexelsImportError = ref('')
const pexelsImportWarning = ref(false)
const pexelsImportResult = ref<ImportResult | null>(null)
const pexelsImportFormRef = ref<{ validate: () => Promise<unknown> } | null>(null)
const pexelsImportForm = ref({
  keyword: '',
  count: 5 as number | undefined,
  category: '',
  tags: [] as string[],
})

let requestSeq = 0
let skipNextRouteLoad = false

const STATUS_OPTIONS = [
  { value: '0', label: '待审核' },
  { value: '1', label: '已通过' },
  { value: '2', label: '已拒绝' },
]

function readQuery() {
  const asString = (value: unknown) => (typeof value === 'string' && value ? value : undefined)
  const current = Number(route.query.current) > 0 ? Number(route.query.current) : 1
  const size = Number(route.query.size) > 0 ? Number(route.query.size) : DEFAULT_PAGE_SIZE
  const status = asString(route.query.status) ?? '0'
  return {
    current,
    size,
    status,
    name: asString(route.query.name),
    tag: asString(route.query.tag),
  }
}

const query = computed(() => readQuery())
const selectedImage = computed(
  () => images.value.find((image) => image.id === selectedImageId.value) ?? images.value[0] ?? null,
)

const status = computed({
  get: () => readQuery().status,
  set: (value: string) => pushQuery({ status: value, current: 1 }),
})

async function load() {
  const { current, size, name, tag, status } = readQuery()
  const seq = ++requestSeq
  loading.value = true
  error.value = null
  try {
    const page = await listAdminImages({
      current,
      size,
      name,
      tag,
      reviewStatus: Number(status) as ReviewStatus,
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
  const merged = { ...readQuery(), ...patch }
  const next: Record<string, string | number> = {}
  if (merged.status && merged.status !== '0') next.status = merged.status
  if (merged.name) next.name = merged.name
  if (merged.tag) next.tag = merged.tag
  if (merged.current > 1) next.current = merged.current
  if (merged.size !== DEFAULT_PAGE_SIZE) next.size = merged.size
  return router.push({ name: 'admin-images', query: next })
}

function resetPexelsImport() {
  pexelsImportForm.value = { keyword: '', count: 5, category: '', tags: [] }
  pexelsImportError.value = ''
  pexelsImportWarning.value = false
  pexelsImportResult.value = null
}

function openPexelsImport() {
  if (!pexelsImportSubmitting.value) resetPexelsImport()
  pexelsImportOpen.value = true
}

function closePexelsImport() {
  pexelsImportOpen.value = false
  if (!pexelsImportSubmitting.value) resetPexelsImport()
}

async function refreshPassedImages() {
  const current = readQuery()
  if (current.status !== '1' || current.current !== 1) {
    skipNextRouteLoad = true
    try {
      await pushQuery({ status: '1', current: 1 })
      await load()
    } finally {
      skipNextRouteLoad = false
    }
    return
  }
  await load()
}

function importSummary(result: ImportResult) {
  return `成功导入 ${result.imported} 张，跳过 ${result.skipped} 张，失败 ${result.failed} 张。`
}

async function submitPexelsImport() {
  if (pexelsImportSubmitting.value || pexelsImportResult.value) return
  pexelsImportError.value = ''
  pexelsImportWarning.value = false
  try {
    await pexelsImportFormRef.value?.validate()
  } catch {
    return
  }

  const keyword = pexelsImportForm.value.keyword.trim()
  const count = pexelsImportForm.value.count ?? 5
  const tags = normalizeTags(pexelsImportForm.value.tags)
  if (!tags.length) tags.push(keyword)
  if (tagsTooLong(tags)) {
    pexelsImportError.value = '标签总长度不能超过 512 个字符'
    return
  }

  pexelsImportSubmitting.value = true
  try {
    const result = await importPexelsImages({
      keyword,
      count,
      category: pexelsImportForm.value.category.trim() || undefined,
      tags,
    })
    pexelsImportResult.value = result
    await refreshPassedImages()
    if (!pexelsImportOpen.value) message.success(`导入完成。${importSummary(result)}`)
  } catch (e) {
    if (e instanceof ApiError && e.code === 'NETWORK_ERROR') {
      pexelsImportWarning.value = true
      await refreshPassedImages()
      if (!pexelsImportOpen.value) {
        message.warning('导入耗时较长或网络中断，请先检查已通过列表中的结果，再决定是否重试。')
      }
    } else if (e instanceof ApiError && (e.code === 'PEXELS_ERROR' || e.status === 502)) {
      pexelsImportError.value = 'Pexels 暂时不可用或配额不足，请稍后重试'
    } else if (e instanceof ApiError && e.status === 401) {
      return
    } else if (e instanceof ApiError && e.status === 403) {
      pexelsImportError.value = '没有导入权限'
    } else if (e instanceof ApiError && e.status === 400) {
      pexelsImportError.value = errorMessage(e)
    } else {
      pexelsImportError.value = '导入失败，请稍后重试'
    }
    if (!pexelsImportOpen.value && pexelsImportError.value) message.error(pexelsImportError.value)
  } finally {
    pexelsImportSubmitting.value = false
    if (!pexelsImportOpen.value) resetPexelsImport()
  }
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
    const parsed = readQuery()
    nameInput.value = parsed.name ?? ''
    tagInput.value = parsed.tag ?? ''
    if (skipNextRouteLoad) return
    load()
  },
  { immediate: true },
)
watch(dataVersion, load)
watch(images, (items) => {
  if (!items.some((image) => image.id === selectedImageId.value)) {
    selectedImageId.value = items[0]?.id ?? null
  }
})

const emptyText = '当前没有符合条件的图片'
</script>

<template>
  <div class="cp-container cp-page">
    <p class="section-kicker">CURATION WORKBENCH</p>
    <h1 class="cp-page-title">图片审核</h1>
    <p class="cp-page-subtitle">默认展示待审核图片，通过或拒绝后列表会立即更新</p>

    <div class="cp-toolbar admin-toolbar">
      <RadioGroup v-model:value="status" button-style="solid">
        <RadioButton v-for="option in STATUS_OPTIONS" :key="option.value" :value="option.value">
          {{ option.label }}
        </RadioButton>
      </RadioGroup>
      <Input
        v-model:value="nameInput"
        placeholder="搜索标题"
        allow-clear
        style="width: 180px"
        @press-enter="submitSearch"
      />
      <Input
        v-model:value="tagInput"
        placeholder="搜索标签"
        allow-clear
        style="width: 180px"
        @press-enter="submitSearch"
      />
      <Button type="primary" @click="submitSearch">搜索</Button>
      <Button v-if="isAdmin()" type="primary" @click="openPexelsImport">从 Pexels 导入</Button>
      <span class="toolbar-count">共 {{ total }} 张</span>
    </div>

    <Skeleton v-if="loading" :paragraph="{ rows: 6 }" active />

    <ErrorState v-else-if="error" message="审核列表加载失败" :description="error" @retry="load" />

    <EmptyState v-else-if="!images.length" :description="emptyText" />

    <div v-else class="review-workbench">
      <aside class="review-queue">
        <div class="review-queue-heading">
          <div>
            <p class="section-kicker">SUBMISSIONS</p>
            <h2>待审核队列</h2>
          </div>
          <span>{{ total }}</span>
        </div>
        <button
          v-for="image in images"
          :key="image.id"
          type="button"
          class="review-queue-item"
          :class="{ 'review-queue-item-active': selectedImage?.id === image.id }"
          @click="selectedImageId = image.id"
        >
          <span class="review-queue-thumb">
            <img v-if="image.thumbnailUrl" :src="image.thumbnailUrl" alt="" loading="lazy" />
            <span v-else>无预览</span>
          </span>
          <span class="review-queue-copy">
            <strong>{{ image.name || '未命名图片' }}</strong>
            <span>{{ image.owner?.name || '未知用户' }} · {{ formatDateTime(image.createTime) }}</span>
            <span v-if="image.tags?.length" class="review-queue-tags">{{ image.tags.slice(0, 2).join(' · ') }}</span>
          </span>
          <span class="review-queue-badges">
            <Tag v-if="image.source === 'PEXELS'" class="source-tag">Pexels</Tag>
            <Tag :color="REVIEW_STATUS_COLOR[image.reviewStatus]">
              {{ REVIEW_STATUS_TEXT[image.reviewStatus] }}
            </Tag>
          </span>
        </button>
        <div class="queue-note">
          <SafetyCertificateOutlined />
          <span>审核通过后，图片将加入共享图库。</span>
        </div>
      </aside>

      <section v-if="selectedImage" class="review-detail">
        <div class="review-preview">
          <img
            v-if="selectedImage.url || selectedImage.thumbnailUrl"
            :src="selectedImage.url || selectedImage.thumbnailUrl || undefined"
            :alt="selectedImage.name || '待审核图片'"
          />
          <div class="review-preview-meta">
            <span>RAW MASTER · {{ formatDimensions(selectedImage.picWidth, selectedImage.picHeight) }}</span>
            <span>{{ selectedImage.picFormat || 'IMAGE' }}</span>
          </div>
        </div>

        <div class="review-detail-body">
          <div class="review-detail-heading">
            <div>
              <p class="section-kicker">CURATION WORKBENCH</p>
              <h2>{{ selectedImage.name || '未命名图片' }}</h2>
            </div>
            <Tag :color="REVIEW_STATUS_COLOR[selectedImage.reviewStatus]">
              {{ REVIEW_STATUS_TEXT[selectedImage.reviewStatus] }}
            </Tag>
          </div>
          <p class="review-description">{{ selectedImage.introduction || '暂无图片简介。' }}</p>
          <div v-if="selectedImage.source === 'PEXELS'" class="pexels-attribution">
            <Tag>图片来源：Pexels</Tag>
            <span v-if="selectedImage.photographer">摄影师：
              <a
                v-if="selectedImage.photographerUrl"
                :href="selectedImage.photographerUrl"
                target="_blank"
                rel="noopener noreferrer"
              >{{ selectedImage.photographer }}</a>
              <span v-else>{{ selectedImage.photographer }}</span>
            </span>
            <a
              v-if="selectedImage.sourcePageUrl"
              :href="selectedImage.sourcePageUrl"
              target="_blank"
              rel="noopener noreferrer"
            >查看 Pexels 来源</a>
          </div>
          <div class="review-tags">
            <Tag v-if="selectedImage.category">{{ selectedImage.category }}</Tag>
            <Tag v-for="tag in selectedImage.tags" :key="tag">{{ tag }}</Tag>
          </div>
          <div class="review-metadata">
            <div><span>上传者</span><strong>{{ selectedImage.owner?.name || '未知用户' }}</strong></div>
            <div><span>上传时间</span><strong>{{ formatDateTime(selectedImage.createTime) }}</strong></div>
            <div><span>图片尺寸</span><strong>{{ formatDimensions(selectedImage.picWidth, selectedImage.picHeight) }}</strong></div>
            <div><span>审核状态</span><strong>{{ REVIEW_STATUS_TEXT[selectedImage.reviewStatus] }}</strong></div>
          </div>
          <div v-if="selectedImage.reviewStatus === 2 && selectedImage.reviewMessage" class="admin-reason">
            拒绝理由：{{ selectedImage.reviewMessage }}
          </div>
          <div class="review-actions">
            <Button type="primary" @click="openReview(selectedImage, 1)">通过并加入图库</Button>
            <Button danger @click="openReview(selectedImage, 2)">拒绝审核</Button>
            <Button @click="confirmDeleteImage(selectedImage, load)">
              <template #icon><DeleteOutlined /></template>
              删除图片
            </Button>
          </div>
        </div>
      </section>
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

    <Modal
      v-model:open="pexelsImportOpen"
      title="从 Pexels 导入"
      :width="600"
      @cancel="closePexelsImport"
    >
      <template v-if="pexelsImportResult">
        <div class="import-result-heading">导入完成</div>
        <div class="import-result-stats">
          <div><span>成功导入</span><strong>{{ pexelsImportResult.imported }}</strong></div>
          <div><span>跳过</span><strong>{{ pexelsImportResult.skipped }}</strong></div>
          <div :class="{ 'import-stat-failed': pexelsImportResult.failed > 0 }">
            <span>失败</span><strong>{{ pexelsImportResult.failed }}</strong>
          </div>
        </div>
        <Alert
          v-if="pexelsImportResult.failed > 0"
          class="import-result-message"
          type="warning"
          show-icon
          :message="importSummary(pexelsImportResult)"
          description="可以减少导入数量后重试。"
        />
        <Alert
          v-else-if="pexelsImportResult.imported === 0 && pexelsImportResult.skipped > 0"
          class="import-result-message"
          type="info"
          show-icon
          message="这些图片已经导入过"
        />
        <p v-else class="import-result-message">{{ importSummary(pexelsImportResult) }}</p>
      </template>

      <template v-else>
        <Form
          ref="pexelsImportFormRef"
          layout="vertical"
          :model="pexelsImportForm"
        >
          <FormItem
            label="关键词"
            name="keyword"
            :rules="[
              { required: true, whitespace: true, message: '请输入搜索关键词' },
              { max: 64, message: '关键词不能超过 64 个字符' },
            ]"
          >
            <Input
              v-model:value="pexelsImportForm.keyword"
              :maxlength="64"
              :disabled="pexelsImportSubmitting"
              placeholder="例如：校园风景"
            />
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
              v-model:value="pexelsImportForm.count"
              :min="1"
              :max="10"
              :precision="0"
              :disabled="pexelsImportSubmitting"
              style="width: 100%"
            />
          </FormItem>
          <FormItem
            label="分类"
            name="category"
            :rules="[{ max: 64, message: '分类不能超过 64 个字符' }]"
          >
            <Input
              v-model:value="pexelsImportForm.category"
              :maxlength="64"
              :disabled="pexelsImportSubmitting"
              placeholder="可选"
            />
          </FormItem>
          <FormItem label="标签" name="tags">
            <Select
              v-model:value="pexelsImportForm.tags"
              mode="tags"
              :token-separators="[',', '，']"
              :open="false"
              :disabled="pexelsImportSubmitting"
              placeholder="可选，未填写时自动使用关键词"
            />
          </FormItem>
        </Form>
        <p class="import-form-note">
          图片将下载后保存到本站 COS。仅用于毕业设计演示，请保留 Pexels 来源署名。
        </p>
        <p class="import-form-note">本次最多导入 10 张，导入过程可能需要几十秒。</p>
        <Alert
          v-if="pexelsImportWarning"
          class="import-form-message"
          type="warning"
          show-icon
          message="导入耗时较长或网络中断"
          description="请先检查已通过列表中的导入结果，再决定是否重试。"
        />
        <Alert
          v-if="pexelsImportError"
          class="import-form-message"
          type="error"
          show-icon
          message="导入失败"
          :description="pexelsImportError"
        />
      </template>

      <template #footer>
        <template v-if="pexelsImportResult">
          <Button type="primary" @click="closePexelsImport">查看审核列表</Button>
          <Button @click="closePexelsImport">关闭</Button>
        </template>
        <template v-else>
          <Button @click="closePexelsImport">取消</Button>
          <Button type="primary" :loading="pexelsImportSubmitting" @click="submitPexelsImport">
            开始导入
          </Button>
        </template>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.admin-toolbar {
  margin-top: 20px;
}

.toolbar-count {
  margin-left: auto;
  color: var(--cp-text-soft);
  font-size: 13px;
}

.section-kicker {
  margin: 0 0 4px;
  color: var(--cp-text-soft);
  font-size: 10px;
  letter-spacing: 0.13em;
}

.review-workbench {
  display: grid;
  grid-template-columns: minmax(280px, 0.78fr) minmax(0, 1.6fr);
  gap: 20px;
  align-items: start;
}

.review-queue,
.review-detail {
  border: 1px solid var(--cp-border);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 8px 28px rgba(20, 27, 36, 0.04);
}

.review-queue {
  padding: 14px;
}

.review-queue-heading,
.review-detail-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.review-queue-heading {
  padding: 5px 3px 12px;
}

.review-queue-heading h2,
.review-detail-heading h2 {
  margin: 0;
}

.review-queue-heading h2 {
  font-size: 16px;
}

.review-queue-heading > span {
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  border-radius: 50%;
  background: #f1f3f5;
  font-size: 12px;
  font-weight: 600;
}

.review-queue-item {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr) auto;
  gap: 10px;
  width: 100%;
  align-items: center;
  margin-top: 8px;
  padding: 9px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: #f8f9fa;
  cursor: pointer;
  text-align: left;
}

.review-queue-item-active {
  border-color: #cbd0d5;
  background: #fff;
  box-shadow: 0 4px 12px rgba(20, 27, 36, 0.06);
}

.review-queue-thumb {
  display: grid;
  width: 72px;
  height: 62px;
  place-items: center;
  overflow: hidden;
  border-radius: 7px;
  background: #eceff1;
  color: var(--cp-text-soft);
  font-size: 10px;
}

.review-queue-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.review-queue-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.review-queue-copy strong,
.review-queue-copy span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.review-queue-copy strong {
  font-size: 12px;
}

.review-queue-copy span {
  color: var(--cp-text-soft);
  font-size: 10px;
}

.review-queue-item :deep(.ant-tag) {
  margin-inline-end: 0;
  font-size: 10px;
}

.review-queue-badges {
  display: flex;
  align-items: center;
  gap: 4px;
}

.queue-note {
  display: flex;
  gap: 8px;
  margin: 14px 3px 1px;
  color: var(--cp-text-soft);
  font-size: 11px;
}

.review-detail {
  overflow: hidden;
}

.review-preview {
  position: relative;
  display: grid;
  min-height: 280px;
  max-height: 420px;
  place-items: center;
  overflow: hidden;
  background: #202326;
}

.review-preview img {
  display: block;
  width: 100%;
  max-height: 420px;
  object-fit: contain;
}

.review-preview-meta {
  position: absolute;
  right: 12px;
  bottom: 12px;
  left: 12px;
  display: flex;
  justify-content: space-between;
  color: #fff;
  font-size: 10px;
  text-shadow: 0 1px 5px #000;
}

.review-detail-body {
  padding: 18px 20px 20px;
}

.review-detail-heading h2 {
  font-size: 20px;
}

.review-description {
  margin: 10px 0;
  color: var(--cp-text-soft);
  font-size: 13px;
}

.pexels-attribution {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin: 10px 0;
  color: var(--cp-text-soft);
  font-size: 12px;
}

.pexels-attribution a {
  text-decoration: underline;
  text-underline-offset: 2px;
}

.pexels-attribution :deep(.ant-tag) {
  margin-inline-end: 0;
}

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
  border-radius: 10px;
  background: #f8f9fa;
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

.import-result-message {
  margin-top: 14px;
}

.import-form-note {
  margin: 4px 0;
  color: var(--cp-text-soft);
  font-size: 12px;
}

.import-form-message {
  margin-top: 12px;
}

.review-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
}

.review-metadata {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 14px;
  padding: 12px;
  border-radius: 9px;
  background: #f6f7f8;
}

.review-metadata div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.review-metadata span {
  color: var(--cp-text-soft);
  font-size: 10px;
}

.review-metadata strong {
  overflow: hidden;
  font-size: 11px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-reason {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #fff3f1;
  color: #b42318;
  font-size: 12px;
}

.review-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}

.review-actions :deep(.ant-btn-primary) {
  min-width: 170px;
}

@media (max-width: 900px) {
  .review-workbench {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 575px) {
  .review-queue-item {
    grid-template-columns: 58px minmax(0, 1fr) auto;
    gap: 8px;
    padding: 7px;
  }

  .review-queue-thumb {
    width: 58px;
    height: 52px;
  }

  .review-preview {
    min-height: 220px;
  }

  .review-detail-body {
    padding: 15px;
  }

  .review-actions :deep(.ant-btn-primary) {
    width: 100%;
  }

  .toolbar-count {
    margin-left: 0;
  }
}
</style>
