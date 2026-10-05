import type { AiReviewVerdict, ImageVO } from '../api/types'

/** 徽标/区块色调，组件据此映射到既有 --cp-status-* 令牌（不新增颜色） */
export type AiReviewTone = 'pass' | 'block' | 'review' | 'error'

export interface AiReviewBadge {
  text: string
  tone: AiReviewTone
}

export interface AiReviewDetail {
  title: string
  tone: AiReviewTone
}

/** SKIP = 功能上线前的存量图，AI 不处理，不展示任何标识 */
type DisplayableVerdict = Exclude<AiReviewVerdict, 'SKIP'>

const BADGE: Record<DisplayableVerdict, AiReviewBadge> = {
  PASS: { text: 'AI 已过', tone: 'pass' },
  BLOCK: { text: 'AI 拦截', tone: 'block' },
  REVIEW: { text: 'AI 疑似', tone: 'review' },
  ERROR: { text: 'AI 失败', tone: 'error' },
}

const DETAIL: Record<DisplayableVerdict, AiReviewDetail> = {
  PASS: { title: 'AI 自动通过', tone: 'pass' },
  BLOCK: { title: 'AI 自动拒绝', tone: 'block' },
  REVIEW: { title: 'AI 疑似违规·待人工复核', tone: 'review' },
  ERROR: { title: 'AI 审核失败·需人工处理', tone: 'error' },
}

/** 用户端待审队列提示（不得出现 AI 字样） */
export const MINE_PENDING_ETA_TEXT = '最迟 24 小时出结果'

/** 命中违规类别：逗号串 → 数组；空串/null → [] */
export function aiLabels(image: ImageVO): string[] {
  const raw = image.aiReviewLabels
  if (!raw) return []
  return raw
    .split(',')
    .map((label) => label.trim())
    .filter(Boolean)
}

/** AI 结论是否可展示：null 与 SKIP 都不展示 */
function displayableVerdict(image: ImageVO): DisplayableVerdict | null {
  const verdict = image.aiReviewVerdict
  return verdict && verdict !== 'SKIP' ? verdict : null
}

/** 管理端队列小徽标 */
export function aiBadge(image: ImageVO): AiReviewBadge | null {
  const verdict = displayableVerdict(image)
  return verdict ? BADGE[verdict] : null
}

/** 管理端详情结论（同时用作徽标的可读全称） */
export function aiDetail(image: ImageVO): AiReviewDetail | null {
  const verdict = displayableVerdict(image)
  return verdict ? DETAIL[verdict] : null
}

/** 来源判定：AI 哨兵 0 是 number、人工雪花 id 是 string，故统一转字符串比较 */
export function isAiSource(image: ImageVO): boolean {
  return String(image.reviewerId) === '0'
}

/** 用户端审核结果文案（不含 AI 字样、不含置信度） */
export function mineReviewHint(image: ImageVO): string {
  if (image.reviewStatus === 1) {
    return '审核已通过，已收录进图库'
  }
  if (image.reviewStatus === 2) {
    const labels = aiLabels(image)
    return labels.length
      ? `审核未通过：疑似${labels.join('、')}`
      : '审核未通过，如有疑问请联系管理员'
  }
  return '系统审核中，结果最迟 24 小时内更新'
}