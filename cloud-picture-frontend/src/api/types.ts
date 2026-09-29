// 与后端 ApiResponse / PageData / DTO 一一对应的前端类型。
// 雪花 ID 超出 JS 安全整数范围，http 层会在 JSON.parse 前把长整数转成字符串，因此 ID 统一按 string 处理。
export type Id = string

export interface ApiResponse<T> {
  code: string
  message: string
  data: T
}

export interface PageData<T> {
  records: T[]
  total: number
  current: number
  size: number
}

/** 0 待审核 / 1 已通过 / 2 已拒绝 */
export type ReviewStatus = 0 | 1 | 2

/** 0 禁用 / 1 正常 */
export type UserStatus = 0 | 1

export type Role = 'USER' | 'ADMIN'

export interface UserBrief {
  id: Id
  name: string | null
  avatar: string | null
}

export interface ImageVO {
  id: Id
  /** 短期签名预览地址，随每次响应变化，不做本地长期缓存 */
  url: string | null
  thumbnailUrl: string | null
  name: string | null
  introduction: string | null
  category: string | null
  tags: string[] | null
  picSize: number | null
  picWidth: number | null
  picHeight: number | null
  picFormat: string | null
  ownerId: Id | null
  reviewStatus: ReviewStatus
  reviewMessage: string | null
  createTime: string | null
  /** 仅列表接口填充（详情接口不返回上传者） */
  owner: UserBrief | null
}

export interface UserVO {
  id: Id
  account: string
  name: string | null
  avatar: string | null
  profile: string | null
  role: Role
  status: UserStatus
  createTime: string | null
}

export interface LoginUserVO {
  id: Id
  account: string
  name: string | null
  avatar: string | null
  role: Role
  token: string
}

export interface PageQuery {
  current: number
  size: number
}

export interface ImageQuery extends PageQuery {
  name?: string
  category?: string
  tag?: string
  reviewStatus?: ReviewStatus
}