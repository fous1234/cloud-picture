<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Button, FloatButton } from 'ant-design-vue'
import { ClockCircleOutlined, CloudDownloadOutlined } from '@ant-design/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import type { RouteLocationRaw } from 'vue-router'
import { listAdminImages } from '../api/admin-image'
import { listMyImages } from '../api/image'
import { isAdmin } from '../stores/session'
import { dataVersion, openPexelsImport, openUpload } from '../stores/ui'
import type { ReviewStatus } from '../api/types'
import ImageManagementMinePanel from '../components/ImageManagementMinePanel.vue'
import ImageManagementReviewPanel from '../components/ImageManagementReviewPanel.vue'
import AppBreadcrumb from '../components/AppBreadcrumb.vue'

const route = useRoute()
const router = useRouter()
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

const managementCrumbs: { label: string; to?: RouteLocationRaw }[] = [
  { label: '首页', to: { name: 'gallery' } },
  { label: '图片管理' },
]

const activeTab = computed<'review' | 'mine'>(() => {
  if (!isAdmin()) return 'mine'
  return route.query.tab === 'mine' ? 'mine' : 'review'
})

const approvedPercent = computed(() => {
  if (mineStats.total === null || mineStats.approved === null || mineStats.total === 0) return null
  return Math.round((mineStats.approved / mineStats.total) * 1000) / 10
})

const statCards = computed(() => {
  if (isAdmin()) {
    return [
      { label: '待决审核申请', chip: '待审核', value: reviewStats.pending, unit: '幅资产待质检', tone: 'pending' },
      { label: '全库公开收录', chip: '已入库', value: reviewStats.approved, unit: '幅已对外开放', tone: 'approved' },
      { label: '退回修改与驳回', chip: '已驳回', value: reviewStats.rejected, unit: '幅已附带意见退回', tone: 'rejected' },
    ]
  }
  return [
    { label: '累计上传作品', chip: '全部', value: mineStats.total, unit: '幅作品', tone: 'neutral' },
    {
      label: '已入库共享',
      chip: '已入库',
      value: mineStats.approved,
      unit: approvedPercent.value === null ? '幅已对外开放' : `幅已对外开放 · ${approvedPercent.value}%`,
      tone: 'approved',
    },
    { label: '正在审核中', chip: '审核中', value: mineStats.pending, unit: '幅待处理', tone: 'pending' },
  ]
})

function displayCount(value: number | null) {
  if (statsLoading.value) return '…'
  return value === null ? '—' : value
}

function selectTab(tab: 'review' | 'mine') {
  router.push({ name: 'image-management', query: { tab } })
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
    const mineCounts = await Promise.all([countMine(), countMine(1), countMine(0), countMine(2)])
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

watch(dataVersion, loadStats)
loadStats()
</script>

<template>
  <div class="cp-container cp-page image-management-page">
    <section class="management-head">
      <div class="management-head-top">
        <AppBreadcrumb :items="managementCrumbs" />
        <span class="management-workbench-badge">
          <span class="management-workbench-dot"></span>
          CURATION WORKBENCH
        </span>
      </div>

      <div class="management-title-row">
        <div class="management-title-main">
          <div class="management-title-line">
            <h1 class="cp-page-title">图片管理</h1>
            <span class="management-role">{{ isAdmin() ? '管理员视角' : '普通用户视角' }}</span>
          </div>
          <p class="cp-page-subtitle">
            {{ isAdmin() ? '统一管理全站图片审核流与个人上传资产，左右两栏快速核验与流转。' : '集中管理本人上传的摄影作品资产与审核动态，左右两栏快速核验与流转。' }}
          </p>
        </div>

        <div v-if="isAdmin()" class="management-actions">
          <Button class="management-import-button" @click="openPexelsImport">
            <template #icon><CloudDownloadOutlined /></template>
            从 Pexels 导入
          </Button>
        </div>
      </div>

      <div class="management-stats">
        <div
          v-for="card in statCards"
          :key="card.label"
          class="management-stat-card"
          :class="`management-stat-card-${card.tone}`"
        >
          <div class="management-stat-head">
            <span class="management-stat-label">{{ card.label }}</span>
            <span class="management-stat-chip">{{ card.chip }}</span>
          </div>
          <div class="management-stat-value">
            <strong>{{ displayCount(card.value) }}</strong>
            <span class="management-stat-unit">{{ card.unit }}</span>
          </div>
        </div>
      </div>
    </section>

    <section class="management-tabs-bar">
      <div class="management-tabs" role="tablist" aria-label="图片管理视图">
        <button
          v-if="isAdmin()"
          type="button"
          role="tab"
          :aria-selected="activeTab === 'review'"
          class="management-tab"
          :class="{ 'management-tab-active': activeTab === 'review' }"
          @click="selectTab('review')"
        >
          <span>图片审核</span>
          <span class="management-tab-count">{{ displayCount(reviewStats.pending) }}</span>
        </button>
        <button
          type="button"
          role="tab"
          :aria-selected="activeTab === 'mine'"
          class="management-tab"
          :class="{ 'management-tab-active': activeTab === 'mine' }"
          @click="selectTab('mine')"
        >
          <span>我的上传</span>
          <span class="management-tab-count">{{ displayCount(reviewStats.mine) }}</span>
        </button>
      </div>
      <div class="management-sort-hint">
        <ClockCircleOutlined />
        <span>按提交时间倒序排列</span>
      </div>
    </section>

    <ImageManagementReviewPanel v-if="activeTab === 'review'" :stats="reviewStats" />
    <ImageManagementMinePanel v-else :stats="mineStats" @upload="openUpload" />

    <!-- 长列表回到顶部：滚动超过约一屏距离后才出现，平滑回顶 -->
    <FloatButton.BackTop :visibility-height="600" tooltip="回到顶部" />
  </div>
</template>

<style scoped>
.image-management-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.management-head {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.management-head-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.management-workbench-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px;
  border: 1px solid var(--cp-border);
  border-radius: 999px;
  background: var(--cp-surface);
  box-shadow: var(--cp-shadow-subtle);
  color: var(--cp-text-soft);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
  letter-spacing: 0.06em;
}

.management-workbench-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #10b981;
}

.management-title-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}

.management-title-main {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.management-title-line {
  display: flex;
  align-items: center;
  gap: 12px;
}

.management-title-line .cp-page-title {
  margin: 0;
}

.management-role {
  padding: 2px 10px;
  border: 1px solid var(--cp-border);
  border-radius: 999px;
  background: var(--cp-bg-soft);
  color: var(--cp-text-soft);
  font-size: 12px;
  font-weight: 500;
}

.management-actions {
  display: flex;
  flex: none;
  gap: 8px;
}

.management-import-button {
  height: 40px;
  border-radius: var(--cp-radius);
  font-weight: 600;
}

.management-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.management-stat-card {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px;
  border: 1px solid var(--cp-border);
  border-radius: var(--cp-radius-lg);
  background: var(--cp-surface);
  box-shadow: var(--cp-shadow-subtle);
  transition: box-shadow 0.2s ease;
}

.management-stat-card:hover {
  box-shadow: var(--cp-shadow-card);
}

.management-stat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.management-stat-label {
  color: var(--cp-text-soft);
  font-size: 12px;
  font-weight: 500;
}

.management-stat-chip {
  padding: 2px 8px;
  border: 1px solid var(--cp-border);
  border-radius: 999px;
  background: var(--cp-bg-soft);
  color: var(--cp-text-soft);
  font-size: 11px;
  font-weight: 600;
}

.management-stat-card-pending .management-stat-chip {
  border-color: var(--cp-status-pending-border);
  background: var(--cp-status-pending-bg);
  color: var(--cp-status-pending-fg);
}

.management-stat-card-approved .management-stat-chip {
  border-color: var(--cp-status-approved-border);
  background: var(--cp-status-approved-bg);
  color: var(--cp-status-approved-fg);
}

.management-stat-card-rejected .management-stat-chip {
  border-color: var(--cp-status-rejected-border);
  background: var(--cp-status-rejected-bg);
  color: var(--cp-status-rejected-fg);
}

.management-stat-value {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.management-stat-value strong {
  color: var(--cp-text);
  font-size: 30px;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.management-stat-card-pending .management-stat-value strong {
  color: var(--cp-status-pending-fg);
}

.management-stat-card-approved .management-stat-value strong {
  color: var(--cp-status-approved-fg);
}

.management-stat-card-rejected .management-stat-value strong {
  color: var(--cp-status-rejected-fg);
}

.management-stat-unit {
  color: var(--cp-text-muted);
  font-size: 12px;
}

.management-tabs-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--cp-border);
}

.management-tabs {
  display: flex;
  align-items: center;
  gap: 8px;
}

.management-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 1px solid transparent;
  border-radius: var(--cp-radius);
  background: transparent;
  color: var(--cp-text-soft);
  font: inherit;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: color 0.15s ease, background 0.15s ease;
}

.management-tab:hover {
  background: var(--cp-surface);
  color: var(--cp-text);
}

.management-tab-active {
  background: var(--cp-surface);
  border-color: var(--cp-border);
  color: var(--cp-text);
  font-weight: 700;
  box-shadow: var(--cp-shadow-subtle);
}

.management-tab-count {
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--cp-bg-soft);
  color: var(--cp-text-soft);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
}

.management-tab-active .management-tab-count {
  background: var(--cp-accent);
  color: #fff;
}

.management-sort-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--cp-text-muted);
  font-size: 12px;
}

@media (max-width: 991px) {
  .management-stats {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 575px) {
  .management-head-top {
    flex-wrap: wrap;
  }

  .management-title-row {
    align-items: stretch;
    flex-direction: column;
  }

  .management-import-button {
    width: 100%;
  }

  .management-tabs-bar {
    flex-wrap: wrap;
  }

  .management-tab {
    flex: 1;
    justify-content: center;
  }
}
</style>