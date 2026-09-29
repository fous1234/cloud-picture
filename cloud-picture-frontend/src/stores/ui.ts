import { ref } from 'vue'
import type { ImageVO, ReviewStatus, UserBrief, UserStatus } from '../api/types'

/** 上传弹窗由导航打开，弹窗本身挂在共享布局上 */
export const uploadOpen = ref(false)

/** 图片信息编辑弹窗（图库、详情、我的上传共用） */
export const editingImage = ref<ImageVO | null>(null)

/** 管理员审核弹窗 */
export const reviewingImage = ref<ImageVO | null>(null)
/** 审核弹窗打开时的默认选项：1 通过 / 2 拒绝 */
export const reviewInitial = ref<1 | 2>(1)

/** 数据版本号：增删改成功后自增，各列表页据此重新拉取 */
export const dataVersion = ref(0)

export function bumpData() {
  dataVersion.value += 1
}

export function openUpload() {
  uploadOpen.value = true
}

export function openEdit(image: ImageVO) {
  editingImage.value = image
}

export function openReview(image: ImageVO, initial: 1 | 2 = 1) {
  reviewingImage.value = image
  reviewInitial.value = initial
}

/**
 * 详情接口不返回上传者信息，列表接口会返回；把列表里见过的上传者缓存下来，
 * 供详情页在接口未补齐时降级展示，避免为此在前端拼装额外请求。
 */
export const ownerCache = new Map<string, UserBrief>()

export function rememberOwners(images: ImageVO[]) {
  for (const image of images) {
    if (image.owner && image.ownerId) ownerCache.set(image.ownerId, image.owner)
  }
}

export const REVIEW_STATUS_TEXT: Record<ReviewStatus, string> = {
  0: '待审核',
  1: '已通过',
  2: '已拒绝',
}

export const REVIEW_STATUS_COLOR: Record<ReviewStatus, string> = {
  0: 'processing',
  1: 'success',
  2: 'error',
}

export const USER_STATUS_TEXT: Record<UserStatus, string> = {
  0: '已禁用',
  1: '正常',
}

/** 后端没有分类字典接口，分类是自由文本，这里只作为输入建议与筛选项 */
export const CATEGORY_OPTIONS = [
  { value: '校园', label: '校园' },
  { value: '风景', label: '风景' },
  { value: '人物', label: '人物' },
  { value: '动物', label: '动物' },
  { value: '建筑', label: '建筑' },
  { value: '活动', label: '活动' },
  { value: '其他', label: '其他' },
]