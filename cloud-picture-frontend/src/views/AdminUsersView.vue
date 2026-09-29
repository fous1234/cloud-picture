<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Avatar, Button, Input, Modal, Pagination, Skeleton, Table, Tag, Tooltip, message } from 'ant-design-vue'
import type { TableColumnsType } from 'ant-design-vue'
import type { UserStatus, UserVO } from '../api/types'
import { listUsers, updateUserStatus } from '../api/user'
import { errorMessage } from '../api/http'
import ErrorState from '../components/ErrorState.vue'
import EmptyState from '../components/EmptyState.vue'
import { USER_STATUS_TEXT, dataVersion } from '../stores/ui'
import { session } from '../stores/session'
import { formatDateTime } from '../utils/format'

const route = useRoute()
const router = useRouter()

const DEFAULT_PAGE_SIZE = 10

const users = ref<UserVO[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref<string | null>(null)
const accountInput = ref('')
const nameInput = ref('')
const selectedUser = ref<UserVO | null>(null)
const statusModalOpen = ref(false)
const statusSubmitting = ref(false)

let requestSeq = 0

function readQuery() {
  const asString = (value: unknown) => (typeof value === 'string' && value ? value : undefined)
  const current = Number(route.query.current) > 0 ? Number(route.query.current) : 1
  const size = Number(route.query.size) > 0 ? Number(route.query.size) : DEFAULT_PAGE_SIZE
  return { current, size, account: asString(route.query.account), name: asString(route.query.name) }
}

const query = computed(() => readQuery())

async function load() {
  const { current, size, account, name } = readQuery()
  const seq = ++requestSeq
  loading.value = true
  error.value = null
  try {
    const page = await listUsers({ current, size, account, name })
    if (seq !== requestSeq) return
    users.value = page.records ?? []
    total.value = page.total ?? 0
  } catch (e) {
    if (seq !== requestSeq) return
    users.value = []
    total.value = 0
    error.value = errorMessage(e)
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

function pushQuery(patch: Record<string, string | number | undefined>) {
  const merged = { ...readQuery(), ...patch }
  const next: Record<string, string | number> = {}
  if (merged.account) next.account = merged.account
  if (merged.name) next.name = merged.name
  if (merged.current > 1) next.current = merged.current
  if (merged.size !== DEFAULT_PAGE_SIZE) next.size = merged.size
  router.push({ name: 'admin-users', query: next })
}

function submitSearch() {
  pushQuery({
    account: accountInput.value.trim() || undefined,
    name: nameInput.value.trim() || undefined,
    current: 1,
  })
}

function changePage(page: number, size: number) {
  pushQuery({ current: page, size })
}

function isSelf(user: { id: string }) {
  return session.user?.id === user.id
}

async function toggleStatus(user: UserVO) {
  selectedUser.value = user
  statusModalOpen.value = true
}

async function confirmStatusChange() {
  const user = selectedUser.value
  if (!user || statusSubmitting.value) return
  const disabling = user.status === 1
  statusSubmitting.value = true
  try {
    await updateUserStatus(user.id, disabling ? 0 : 1)
    statusModalOpen.value = false
    message.success(disabling ? '账号已禁用' : '账号已启用')
    load()
  } catch (e) {
    message.error(errorMessage(e))
    throw e
  } finally {
    statusSubmitting.value = false
  }
}

const columns: TableColumnsType = [
  { title: '账号', dataIndex: 'account', key: 'account' },
  { title: '昵称', dataIndex: 'name', key: 'name' },
  { title: '角色', key: 'role', width: 110 },
  { title: '状态', key: 'status', width: 100 },
  { title: '创建时间', key: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 120 },
]

// 窄屏用纵向卡片替代表格，避免在小屏横向滚动
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
    accountInput.value = readQuery().account ?? ''
    nameInput.value = readQuery().name ?? ''
    load()
  },
  { immediate: true },
)
watch(dataVersion, load)
</script>

<template>
  <div class="cp-container cp-page">
    <p class="section-kicker">ACCESS CONTROL · 实时同步</p>
    <h1 class="cp-page-title">用户管理</h1>
    <p class="cp-page-subtitle">按账号或昵称查找用户，启用或禁用账号</p>

    <div class="cp-toolbar admin-toolbar">
      <Input
        v-model:value="accountInput"
        placeholder="搜索账号"
        allow-clear
        style="width: 180px"
        @press-enter="submitSearch"
      />
      <Input
        v-model:value="nameInput"
        placeholder="搜索昵称"
        allow-clear
        style="width: 180px"
        @press-enter="submitSearch"
      />
      <Button type="primary" @click="submitSearch">搜索</Button>
      <span class="toolbar-count">共 {{ total }} 个用户</span>
    </div>

    <Skeleton v-if="loading" :paragraph="{ rows: 6 }" active />

    <ErrorState v-else-if="error" message="用户列表加载失败" :description="error" @retry="load" />

    <EmptyState v-else-if="!users.length" description="没有符合条件的用户" />

    <div v-else-if="!narrow" class="user-table-panel">
      <Table
        :columns="columns"
        :data-source="users"
        :pagination="false"
        :row-key="(record: UserVO) => record.id"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'account'">{{ record.account }}</template>
          <template v-else-if="column.key === 'name'">{{ record.name || '—' }}</template>
          <template v-else-if="column.key === 'role'">
            <Tag :color="record.role === 'ADMIN' ? 'gold' : 'blue'">
              {{ record.role === 'ADMIN' ? '管理员' : '普通用户' }}
            </Tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <Tag :color="record.status === 1 ? 'success' : 'error'">
              {{ USER_STATUS_TEXT[record.status as UserStatus] }}
            </Tag>
          </template>
          <template v-else-if="column.key === 'createTime'">
            {{ formatDateTime(record.createTime) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <Tooltip :title="isSelf(record as UserVO) ? '不能禁用当前登录的管理员账号' : undefined">
              <Button
                size="small"
                :danger="record.status === 1"
                :disabled="isSelf(record as UserVO)"
                @click="toggleStatus(record as UserVO)"
              >
                {{ record.status === 1 ? '禁用' : '启用' }}
              </Button>
            </Tooltip>
          </template>
        </template>
      </Table>
    </div>

    <div v-else class="user-cards">
      <article v-for="user in users" :key="user.id" class="user-card">
        <div class="user-card-head">
          <span class="user-account">{{ user.account }}</span>
          <Tag :color="user.status === 1 ? 'success' : 'error'">
            {{ USER_STATUS_TEXT[user.status] }}
          </Tag>
        </div>
        <div class="user-card-meta">昵称：{{ user.name || '—' }}</div>
        <div class="user-card-meta">
          角色：{{ user.role === 'ADMIN' ? '管理员' : '普通用户' }}
        </div>
        <div class="user-card-meta">创建时间：{{ formatDateTime(user.createTime) }}</div>
        <Tooltip :title="isSelf(user) ? '不能禁用当前登录的管理员账号' : undefined">
          <Button
            block
            :danger="user.status === 1"
            :disabled="isSelf(user)"
            @click="toggleStatus(user)"
          >
            {{ user.status === 1 ? '禁用' : '启用' }}
          </Button>
        </Tooltip>
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
      v-model:open="statusModalOpen"
      :title="selectedUser?.status === 1 ? '禁用用户账号' : '启用用户账号'"
      :confirm-loading="statusSubmitting"
      :mask-closable="!statusSubmitting"
      :keyboard="!statusSubmitting"
      :ok-text="selectedUser?.status === 1 ? '确认禁用' : '确认启用'"
      cancel-text="取消"
      :ok-button-props="{ danger: selectedUser?.status === 1 }"
      @ok="confirmStatusChange"
      @cancel="selectedUser = null"
    >
      <template v-if="selectedUser">
        <p class="status-modal-copy">
          {{ selectedUser.status === 1
            ? '禁用后该用户将无法登录，现有会话会立即失效。'
            : '启用后该用户可以重新登录并使用图库。' }}
        </p>
        <div class="status-modal-user">
          <Avatar :src="undefined" :size="40">{{ (selectedUser.name || selectedUser.account).slice(0, 1) }}</Avatar>
          <div>
            <strong>{{ selectedUser.name || selectedUser.account }}</strong>
            <span>{{ selectedUser.account }} · {{ selectedUser.role === 'ADMIN' ? '管理员' : '普通用户' }}</span>
          </div>
          <Tag :color="selectedUser.status === 1 ? 'success' : 'error'">
            {{ USER_STATUS_TEXT[selectedUser.status] }}
          </Tag>
        </div>
        <div v-if="selectedUser.status === 1" class="status-modal-warning">
          禁用后所有已登录设备都会退出，需要管理员重新启用账号。
        </div>
      </template>
    </Modal>
  </div>
</template>

<style scoped>
.admin-toolbar {
  margin-top: 20px;
}

.section-kicker {
  margin: 0 0 4px;
  color: var(--cp-text-soft);
  font-size: 10px;
  letter-spacing: 0.13em;
}

.user-table-panel {
  overflow: hidden;
  border: 1px solid var(--cp-border);
  border-radius: 12px;
  background: #fff;
}

.user-table-panel :deep(.ant-table) {
  background: transparent;
}

.user-table-panel :deep(.ant-table-thead > tr > th) {
  background: #f7f8f9;
  color: #555d65;
  font-size: 11px;
  font-weight: 600;
}

.user-table-panel :deep(.ant-table-tbody > tr > td) {
  border-bottom-color: #eff0f2;
  font-size: 12px;
}

.status-modal-copy {
  margin: 0 0 16px;
  color: var(--cp-text-soft);
}

.status-modal-user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--cp-border);
  border-radius: 10px;
  background: #f8f9fa;
}

.status-modal-user > div {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
}

.status-modal-user strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-modal-user span {
  color: var(--cp-text-soft);
  font-size: 12px;
}

.status-modal-warning {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #fff8e8;
  color: #72541a;
  font-size: 12px;
}

.toolbar-count {
  margin-left: auto;
  color: var(--cp-text-soft);
  font-size: 13px;
}

.user-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.user-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius);
}

.user-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.user-account {
  font-weight: 600;
}

.user-card-meta {
  font-size: 13px;
  color: var(--cp-text-soft);
}

@media (max-width: 767px) {
  .toolbar-count {
    margin-left: 0;
  }
}
</style>
