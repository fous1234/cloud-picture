<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Button } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import { listAdminImages } from '../api/admin-image'
import { listMyImages } from '../api/image'
import { isAdmin } from '../stores/session'
import { dataVersion, openPexelsImport, openUpload } from '../stores/ui'
import type { ReviewStatus } from '../api/types'
import ImageManagementMinePanel from '../components/ImageManagementMinePanel.vue'
import ImageManagementReviewPanel from '../components/ImageManagementReviewPanel.vue'

const route = useRoute()
const router = useRouter()
const batchMode = ref(false)
const selectedIds = ref(new Set<string>())
const statsLoading = ref(false)
const mineStats = reactive<{
  total: number | null
  approved: number | null
  pending: number | null
  rejected: number | null
}>({
  total: null,
  approved: null,
  pending: null,
  rejected: null,
})
const reviewStats = reactive({
  pending: null as number | null,
  approved: null as number | null,
  rejected: null as number | null,
  mine: null as number | null,
})
let statsRequestSeq = 0

const activeTab = computed<'review' | 'mine'>(() => {
  if (!isAdmin()) return 'mine'
  return route.query.tab === 'mine' ? 'mine' : 'review'
})

const approvedPercent = computed(() => {
  if (mineStats.total === null || mineStats.approved === null || mineStats.total === 0) return null
  return Math.round((mineStats.approved / mineStats.total) * 1000) / 10
})

function displayCount(value: number | null) {
  if (statsLoading.value) return '…'
  return value === null ? '—' : value
}

function selectTab(tab: 'review' | 'mine') {
  batchMode.value = false
  selectedIds.value = new Set()
  router.push({ name: 'image-management', query: { tab } })
}

function toggleBatch() {
  batchMode.value = !batchMode.value
  if (!batchMode.value) selectedIds.value = new Set()
}

function toggleSelect(imageId: string) {
  const next = new Set(selectedIds.value)
  if (next.has(imageId)) next.delete(imageId)
  else next.add(imageId)
  selectedIds.value = next
}

async function countMine(reviewStatus?: ReviewStatus) {
  const page = await listMyImages({ current: 1, size: 1, reviewStatus })
  return page.total ?? 0
}

async function countAdmin(reviewStatus: ReviewStatus) {
  const page = await listAdminImages({ current: 1, size: 1, reviewStatus })
  return page.total ?? 0
}

async function loadStats() {
  const seq = ++statsRequestSeq
  statsLoading.value = true
  try {
    const mineCounts = await Promise.all([
      countMine(),
      countMine(1),
      countMine(0),
      countMine(2),
    ])
    if (seq !== statsRequestSeq) return
    mineStats.total = mineCounts[0]
    mineStats.approved = mineCounts[1]
    mineStats.pending = mineCounts[2]
    mineStats.rejected = mineCounts[3]
    reviewStats.mine = mineCounts[0]

    if (isAdmin()) {
      const reviewCounts = await Promise.all([countAdmin(0), countAdmin(1), countAdmin(2)])
      if (seq !== statsRequestSeq) return
      reviewStats.pending = reviewCounts[0]
      reviewStats.approved = reviewCounts[1]
      reviewStats.rejected = reviewCounts[2]
    } else {
      reviewStats.pending = mineStats.pending
      reviewStats.approved = mineStats.approved
      reviewStats.rejected = mineStats.rejected
    }
  } catch {
    if (seq !== statsRequestSeq) return
    mineStats.total = null
    mineStats.approved = null
    mineStats.pending = null
    mineStats.rejected = null
    reviewStats.pending = null
    reviewStats.approved = null
    reviewStats.rejected = null
    reviewStats.mine = null
  } finally {
    if (seq === statsRequestSeq) statsLoading.value = false
  }
}

watch(
  activeTab,
  (tab) => {
    if (tab === 'mine') {
      batchMode.value = false
      selectedIds.value = new Set()
    }
  },
  { immediate: true },
)
watch(dataVersion, loadStats)
loadStats()
</script>

<template>
  <div class="cp-container cp-page image-management-page">
    <div class="management-page-head">
      <div>
        <div class="management-breadcrumb">
          <span>首页</span>
          <span>/</span>
          <strong>图片管理</strong>
        </div>
        <div class="management-title-row">
          <h1 class="cp-page-title">图片管理</h1>
          <span class="management-role">{{ isAdmin() ? '管理员视角' : '普通用户视角' }}</span>
        </div>
        <p class="cp-page-subtitle">
          {{ isAdmin() ? '统一管理全站图片审核流与个人上传作品资产' : '集中管理本人上传的摄影作品资产与审核动态' }}
        </p>
      </div>

      <div class="management-actions">
        <Button v-if="isAdmin()" @click="openPexelsImport">从 Pexels 导入</Button>
        <Button v-if="isAdmin()" :type="batchMode ? 'primary' : 'default'" @click="toggleBatch">
          {{ batchMode ? '退出批量管理' : '批量管理' }}
        </Button>
      </div>
    </div>

    <div v-if="isAdmin()" class="management-tabs" role="tablist" aria-label="图片管理视图">
      <button
        type="button"
        class="management-tab"
        :class="{ 'management-tab-active': activeTab === 'review' }"
        role="tab"
        :aria-selected="activeTab === 'review'"
        @click="selectTab('review')"
      >
        <span>图片审核</span>
        <span class="management-tab-count">{{ displayCount(reviewStats.pending) }}</span>
      </button>
      <button
        type="button"
        class="management-tab"
        :class="{ 'management-tab-active': activeTab === 'mine' }"
        role="tab"
        :aria-selected="activeTab === 'mine'"
        @click="selectTab('mine')"
      >
        <span>我的上传</span>
        <span class="management-tab-count">{{ displayCount(reviewStats.mine) }}</span>
      </button>
    </div>

    <div v-if="!isAdmin()" class="management-stats">
      <div class="management-stat-card">
        <span>累计上传作品</span>
        <strong>{{ displayCount(mineStats.total) }} <small>幅</small></strong>
      </div>
      <div class="management-stat-card management-stat-approved">
        <span>已入库共享</span>
        <strong>
          {{ displayCount(mineStats.approved) }} <small>幅</small>
          <em v-if="approvedPercent !== null">({{ approvedPercent }}%)</em>
        </strong>
      </div>
      <div class="management-stat-card management-stat-pending">
        <span>正在审核中</span>
        <strong>{{ displayCount(mineStats.pending) }} <small>幅</small></strong>
      </div>
    </div>

    <ImageManagementReviewPanel
      v-if="activeTab === 'review'"
      :stats="{ pending: reviewStats.pending, approved: reviewStats.approved, rejected: reviewStats.rejected, mine: reviewStats.mine }"
      :batch-mode="batchMode"
      :selected-ids="selectedIds"
      @toggle-select="toggleSelect($event.id)"
      @clear-selection="selectedIds = new Set()"
    />
    <ImageManagementMinePanel
      v-else
      :stats="{
        total: mineStats.total,
        approved: mineStats.approved,
        pending: mineStats.pending,
        rejected: mineStats.rejected,
      }"
      @upload="openUpload"
    />

    <div v-if="!isAdmin()" class="management-helper">
      本人上传的摄影作品通过审核后，即可完成个人资产入库归档。需要更新标题、说明或分类标签时，可编辑对应作品信息。
    </div>
  </div>
</template>

<style scoped>
.image-management-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.management-page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
}

.management-breadcrumb {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
  color: var(--cp-text-muted);
  font-size: 12px;
}

.management-breadcrumb strong {
  color: var(--cp-text-soft);
  font-weight: 500;
}

.management-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.management-title-row .cp-page-title {
  margin-bottom: 0;
}

.management-role {
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--cp-bg-soft);
  color: var(--cp-text-soft);
  font-size: 11px;
}

.management-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.management-actions :deep(.ant-btn) {
  border-radius: 8px;
}

.management-tabs {
  display: flex;
  align-self: flex-start;
  gap: 4px;
  padding: 4px;
  border-radius: var(--cp-radius-lg);
  background: var(--cp-surface);
  box-shadow: 0 1px 4px rgba(17, 24, 39, 0.04);
}

.management-tab {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--cp-text-soft);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
}

.management-tab-active {
  background: var(--cp-accent);
  color: #fff;
}

.management-tab-count {
  min-width: 24px;
  padding: 2px 6px;
  border-radius: 999px;
  background: var(--cp-bg-soft);
  color: var(--cp-text-soft);
  font-size: 11px;
  text-align: center;
}

.management-tab-active .management-tab-count {
  background: var(--cp-status-pending-bg);
  color: var(--cp-status-pending-fg);
}

.management-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.management-stat-card {
  display: flex;
  min-height: 94px;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  padding: 16px;
  border-radius: var(--cp-radius-lg);
  background: var(--cp-surface);
  box-shadow: 0 1px 4px rgba(17, 24, 39, 0.04);
}

.management-stat-card > span {
  color: var(--cp-text-muted);
  font-size: 11px;
  letter-spacing: 0.06em;
}

.management-stat-card strong {
  color: var(--cp-text);
  font-family: 'Plus Jakarta Sans', Inter, sans-serif;
  font-size: 22px;
  font-weight: 600;
}

.management-stat-card small,
.management-stat-card em {
  color: var(--cp-text-muted);
  font-family: Inter, sans-serif;
  font-size: 12px;
  font-style: normal;
  font-weight: 400;
}

.management-stat-approved strong {
  color: var(--cp-status-approved-fg);
}

.management-stat-pending strong {
  color: var(--cp-status-pending-fg);
}

.management-helper {
  padding: 12px 14px;
  border: 1px solid var(--cp-border-subtle);
  border-radius: var(--cp-radius);
  background: var(--cp-bg-soft);
  color: var(--cp-text-soft);
  font-size: 12px;
  line-height: 1.6;
}

@media (max-width: 767px) {
  .management-page-head {
    align-items: stretch;
    flex-direction: column;
  }

  .management-actions {
    width: 100%;
  }

  .management-actions :deep(.ant-btn) {
    flex: 1;
  }

  .management-stats {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 575px) {
  .management-tabs {
    width: 100%;
  }

  .management-tab {
    flex: 1;
    justify-content: center;
  }
}
</style>
