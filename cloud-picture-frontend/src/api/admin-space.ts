import type { AdminSpaceVO, Id, PageData } from './types'
import { del, get, put } from './http'

export interface AdminSpaceQuery {
  current: number
  size: number
  name?: string
}

/** 管理端只返回空间元信息与统计，不含任何图片地址 */
export function listAdminSpaces(query: AdminSpaceQuery): Promise<PageData<AdminSpaceVO>> {
  return get<PageData<AdminSpaceVO>>('/admin/space/list', query)
}

export function renameAdminSpace(id: Id, name: string): Promise<boolean> {
  return put<boolean>(`/admin/space/${id}`, { name })
}

/** 删除指定空间并级联删除其中全部图片与对象存储文件 */
export function deleteAdminSpace(id: Id): Promise<boolean> {
  return del<boolean>(`/admin/space/${id}`)
}