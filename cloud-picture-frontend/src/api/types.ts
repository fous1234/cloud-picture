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

/** AI 审核结论：PASS 自动通过 / REVIEW 存疑 / BLOCK 拦截 / ERROR 失败 / SKIP 存量跳过 */
export type AiReviewVerdict = 'PASS' | 'REVIEW' | 'BLOCK' | 'ERROR' | 'SKIP'

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
  /** 共享图库列表使用的中等尺寸短期签名地址；其他响应可为空 */
  mediumUrl: string | null
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
  /** 审核人 id：0 = AI 自动审核；人工审核人为雪花 id。
   *  http 层只给 16 位以上整数补引号，故 AI 哨兵 0 到达时是 number、人工 id 是 string，判定统一用 String(...) */
  reviewerId: string | number | null
  /** AI 审核结论；null 表示尚未由 AI 审核（存量图会被后端标为 SKIP） */
  aiReviewVerdict: AiReviewVerdict | null
  /** AI 置信度 0-100 */
  aiReviewConfidence: number | null
  /** AI 命中违规类别，逗号分隔；空串或 null 表示无标签 */
  aiReviewLabels: string | null
  aiReviewTime: string | null
  createTime: string | null
  source: 'LOCAL' | 'PEXELS'
  sourcePageUrl: string | null
  photographer: string | null
  photographerUrl: string | null
  /** 仅列表接口填充（详情接口不返回上传者） */
  owner: UserBrief | null
}

export interface ImageShareVO {
  enabled: boolean
  token: string | null
}

export interface SharedImageVO {
  id: Id
  url: string
  name: string | null
  introduction: string | null
  category: string | null
  tags: string[]
  picSize: number | null
  picWidth: number | null
  picHeight: number | null
  picFormat: string | null
  source: 'LOCAL' | 'PEXELS' | null
  sourcePageUrl: string | null
  photographer: string | null
  photographerUrl: string | null
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
  randomSeed?: number
  reviewStatus?: ReviewStatus
}

/** 私有空间套餐档位；FREE < PRO < MAX */
export type Tier = 'FREE' | 'PRO' | 'MAX'

/** 订单状态机：UNPAID → PAID / CANCELED / CLOSED */
export type OrderStatus = 'UNPAID' | 'PAID' | 'CANCELED' | 'CLOSED'

/** 支付通道：由后端 picture.payment.channel 决定 */
export type OrderChannel = 'MOCK' | 'ALIPAY'

/** 私有空间：一人一个；无空间时接口返回 null */
export interface SpaceVO {
  id: Id
  name: string
  imageCount: number
  totalSize: number
  /** 当前生效档位：已过期由后端按 FREE 返回 */
  tier: Tier
  tierName: string
  /** 套餐到期时间；FREE 为 null */
  tierExpireTime: string | null
  /** 当前档位的图片数量上限 */
  imageLimit: number
  /** 当前档位的容量上限（字节） */
  sizeLimitBytes: number
  createTime: string | null
}

/** 私有空间图片：无审核、AI 审核、来源、分享相关字段 */
export interface SpaceImageVO {
  id: Id
  /** 短期签名预览地址 */
  url: string | null
  /** 带图片处理参数的短期签名缩略图地址 */
  thumbnailUrl: string | null
  name: string | null
  introduction: string | null
  category: string | null
  tags: string[] | null
  picSize: number | null
  picWidth: number | null
  picHeight: number | null
  picFormat: string | null
  createTime: string | null
}

/** 管理端空间视图：只有元信息与统计，没有任何图片地址字段 */
export interface AdminSpaceVO {
  id: Id
  name: string
  imageCount: number
  totalSize: number
  createTime: string | null
  owner: UserBrief | null
}

export interface SpaceImageQuery extends PageQuery {
  name?: string
  category?: string
  tag?: string
}

/** 套餐卡片：purchasable / current / badge 由后端给出，前端只做展示与禁用 */
export interface SpacePlan {
  tier: Tier
  name: string
  imageLimit: number
  sizeLimitBytes: number
  /** 价格（分）；FREE 为 0 */
  priceFen: number
  /** 有效期天数；FREE 亦为 30，展示时须特判为「永久有效」 */
  planDays: number
  purchasable: boolean
  current: boolean
  /** 徽标：当前套餐 / 推荐；其余为 null */
  badge: string | null
}

export interface PlanList {
  /** 是否已创建私有空间；未创建时全部套餐不可购买 */
  spaceCreated: boolean
  currentTier: Tier
  currentTierName: string
  expireTime: string | null
  plans: SpacePlan[]
}

/** 支付订单：金额单位为分；tierExpireTime 仅单笔详情在已支付时回填 */
export interface PaymentOrder {
  orderNo: string
  userId: Id
  tier: Tier
  tierName: string
  amountFen: number
  status: OrderStatus
  channel: OrderChannel
  /** 渠道交易号（支付宝 trade_no）；未支付为 null */
  transactionId: string | null
  payTime: string | null
  /** 订单超时时间，超时后由后端懒关单置 CLOSED */
  expireTime: string | null
  createTime: string | null
  tierExpireTime: string | null
  /** 是否走内置模拟支付（下单/继续支付时返回） */
  mockPay: boolean
  /** 支付宝跳转收银台表单（alipay 通道下单/继续支付时返回） */
  redirectForm: string | null
}

export interface OrderQuery extends PageQuery {
  status?: OrderStatus
}
