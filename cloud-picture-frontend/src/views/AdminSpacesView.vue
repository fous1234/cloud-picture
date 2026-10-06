<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { Avatar, Button, Input, Modal, Pagination, Skeleton, Table, message } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import type { AdminSpaceVO } from '../api/types'
import { deleteAdminSpace, listAdminSpaces, renameAdminSpace } from '../api/admin-space'
import { errorMessage } from '../api/http'
import ErrorState from '../components/ErrorState.vue'
import EmptyState from '../components/EmptyState.vue'
import { formatDateTime, formatSize } from '../utils/format'

const DEFAULT_PAGE_SIZE = 10

const route = useRoute()
const router = useRouter()

const spaces = ref<AdminSpaceVO[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref<string | null>(null)
const nameInput = ref('')
const selectedSpace = ref<AdminSpaceVO | null>(null)
const renameOpen = ref(false)
const renameName = ref('')
const renaming = ref(false)

let requestSeq = 0

function readQuery() {
  const asString = (value: unknown) => (typeof value === 'string' && value ? value : undefined)
  const current = Number(route.query.current) > 0 ? Number(route.query.current) : 1
  const size = Number(route.query.size) > 0 ? Number(route.query.size) : DEFAULT_PAGE_SIZE
  return { current, size, name: asString(route.query.name) }
}

const query = computed(() => readQuery())

async function load() {
  const { current, size, name } = readQuery()
  const seq = ++requestSeq
  loading.value = true
  error.value = null
  try {
    const page = await listAdminSpaces({ current, size, name })
    if (seq !== requestSeq) return
    spaces.value = page.records ?? []
    total.value = page.total ?? 0
  } catch (e) {
    if (seq !== requestSeq) return
    spaces.value = []
    total.value = 0
    error.value = errorMessage(e)
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

function pushQuery(patch: Record<string, string | number | undefined>) {
  const merged = { ...readQuery(), ...patch }
  const next: Record<string, string | number> = {}
  if (merged.name) next.name = merged.name
  if (merged.current > 1) next.current = merged.current
  if (merged.size !== DEFAULT_PAGE_SIZE) next.size = merged.size
  router.push({ name: 'admin-spaces', query: next })
}

function submitSearch() {
  pushQuery({ name: nameInput.value.trim() || undefined, current: 1 })
}

function changePage(page: number, size: number) {
  pushQuery({ current: page, size })
}

function openRename(space: AdminSpaceVO) {
  selectedSpace.value = space
  renameName.value = space.name
  renameOpen.value = true
}

async function confirmRename() {
  const space = selectedSpace.value
  const name = renameName.value.trim()
  if (!space) return
  if (!name) {
    message.warning('空间名称不能为空')
    return
  }
  if (renaming.value) return
  renaming.value = true
  try {
    await renameAdminSpace(space.id, name)
    renameOpen.value = false
    message.success('空间名称已更新')
    load()
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    renaming.value = false
  }
}

function confirmDelete(space: AdminSpaceVO) {
  Modal.confirm({
    title: '确认删除该私有空间？',
    content: space.imageCount > 0
      ? `将删除「${space.name}」及其中 ${space.imageCount} 张图片，对象存储中的文件会一并删除，操作不可撤销。`
      : `将删除「${space.name}」，该空间没有图片。`,
    okText: '删除空间',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await deleteAdminSpace(space.id)
        message.success('私有空间已删除')
        load()
      } catch (e) {
        message.error(errorMessage(e))
        throw e
      }
    },
  })
}

const columns: TableColumnsType = [
  { title: '空间名称', dataIndex: 'name', key: 'name' },
  { title: '所属用户', key: 'owner' },
  { title: '图片数', key: 'imageCount', width: 100 },
  { title: '占用空间', key: 'totalSize', width: 130 },
  { title: '创建时间', key: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 150 },
]

// 窄屏用纵向卡片替代表格，避免横向滚动
const narrow = ref(false)
const mediaQuery = window.matchMedia('(max-width: 767px)')
function syncNarrow(event: MediaQueryList | MediaQueryListEvent) {
  narrow.value = event.matches
}
syncNarrow(mediaQuery)
onMounted(() => mediaQuery.addEventListener('change', syncNarrow))
onUnmounted(() => mediaQuery.removeEventListener('change', syncNarrow))

watch(
  () => route.query,
  () => {
    nameInput.value = readQuery().name ?? ''
    load()
  },
  { immediate: true },
)
</script>

<template>
  <div class="cp-container cp-page admin-spaces-page">
    <p class="section-kicker">SPACE GOVERNANCE · 元信息视图</p>
    <h1 class="cp-page-title">空间管理</h1>
    <p class="cp-page-subtitle">查看与管理各用户的私有空间，管理员看不到空间内的图片内容</p>

    <div class="space-privacy-note">
      私有空间图片对管理员不可见，此处仅展示空间的元信息与统计。
    </div>

    <div class="cp-toolbar admin-spaces-toolbar">
      <Input
        v-model:value="nameInput"
        placeholder="搜索空间名称"
        allow-clear
        style="width: 200px"
        @press-enter="submitSearch"
      />
      <Button type="primary" @click="submitSearch">搜索</Button>
      <span class="toolbar-count">共 {{ total }} 个空间</span>
    </div>

    <Skeleton v-if="loading" :paragraph="{ rows: 6 }" active />

    <ErrorState v-else-if="error" message="空间列表加载失败" :description="error" @retry="load" />

    <EmptyState v-else-if="!spaces.length" description="没有符合条件的私有空间" />

    <div v-else-if="!narrow" class="space-table-panel">
      <Table
        :columns="columns"
        :data-source="spaces"
        :pagination="false"
        :row-key="(record: AdminSpaceVO) => record.id"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'">
            <strong>{{ record.name }}</strong>
          </template>
          <template v-else-if="column.key === 'owner'">
            <span v-if="record.owner" class="space-owner-cell">
              <Avatar :src="record.owner.avatar || undefined" :size="28">
                {{ (record.owner.name || 'U').slice(0, 1) }}
              </Avatar>
              <span>{{ record.owner.name || '—' }}</span>
            </span>
            <span v-else>—</span>
          </template>
          <template v-else-if="column.key === 'imageCount'">
            {{ record.imageCount }} 张
          </template>
          <template v-else-if="column.key === 'totalSize'">
            <span class="space-mono">{{ formatSize(record.totalSize) }}</span>
          </template>
          <template v-else-if="column.key === 'createTime'">
            {{ formatDateTime(record.createTime) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <Button size="small" @click="openRename(record as AdminSpaceVO)">改名</Button>
            <Button size="small" danger @click="confirmDelete(record as AdminSpaceVO)">删除</Button>
          </template>
        </template>
      </Table>
    </div>

    <div v-else class="space-cards">
      <article v-for="space in spaces" :key="space.id" class="space-card">
        <div class="space-card-head">
          <strong>{{ space.name }}</strong>
          <span class="space-mono">{{ formatSize(space.totalSize) }}</span>
        </div>
        <div class="space-card-owner">
          <Avatar v-if="space.owner" :src="space.owner.avatar || undefined" :size="32">
            {{ (space.owner.name || 'U').slice(0, 1) }}
          </Avatar>
          <span>{{ space.owner?.name || space.owner?.id || '—' }}</span>
        </div>
        <div class="space-card-meta">图片数：{{ space.imageCount }} 张</div>
        <div class="space-card-meta">创建时间：{{ formatDateTime(space.createTime) }}</div>
        <div class="space-card-actions">
          <Button block @click="openRename(space)">改名</Button>
          <Button block danger @click="confirmDelete(space)">删除</Button>
        </div>
      </article>
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
      v-model:open="renameOpen"
      title="修改空间名称"
      :confirm-loading="renaming"
      ok-text="保存"
      cancel-text="取消"
      @ok="confirmRename"
      @cancel="selectedSpace = null"
    >
      <Input v-model:value="renameName" :maxlength="64" placeholder="输入空间名称" @press-enter="confirmRename" />
    </Modal>
  </div>
</template>

<style scoped>
.admin-spaces-toolbar {
  margin-top: 24px;
  padding: 12px;
  border: 1px solid var(--cp-border-subtle);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-surface);
}

.space-privacy-note {
  margin-top: 16px;
  padding: 10px 14px;
  border: 1px solid var(--cp-status-pending-border);
  border-radius: var(--cp-radius);
  background: var(--cp-status-pending-bg);
  color: var(--cp-status-pending-fg);
  font-size: 12px;
}

.space-table-panel {
  overflow: hidden;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius-lg);
  background: #fff;
  box-shadow: 0 8px 24px rgba(17, 24, 39, 0.04);
}

.space-table-panel :deep(.ant-table) {
  background: transparent;
}

.space-table-panel :deep(.ant-table-thead > tr > th) {
  background: var(--cp-bg);
  color: var(--cp-text-soft);
  font-size: 11px;
  font-weight: 600;
}

.space-table-panel :deep(.ant-table-tbody > tr > td) {
  border-bottom-color: #eff0f2;
  font-size: 12px;
}

.space-owner-cell {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.space-mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.space-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.space-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-surface);
  box-shadow: 0 8px 24px rgba(17, 24, 39, 0.04);
}

.space-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.space-card-owner {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0;
  font-size: 13px;
  color: var(--cp-text-soft);
}

.space-card-meta {
  color: var(--cp-text-soft);
  font-size: 13px;
}

.space-card-actions {
  display: flex;
  gap: 8px;
  margin-top: 4px;
}

.toolbar-count {
  margin-left: auto;
  color: var(--cp-text-soft);
  font-size: 13px;
}

.space-table-panel :deep(.ant-table-tbody > tr > td:last-child .ant-btn + .ant-btn) {
  margin-left: 8px;
}

@media (max-width: 767px) {
  .toolbar-count {
    margin-left: 0;
  }
}
</style>