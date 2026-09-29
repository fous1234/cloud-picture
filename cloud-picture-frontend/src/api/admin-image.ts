import type { Id, ImageQuery, ImageVO, PageData } from './types'
import { del, get, post } from './http'
import { rememberOwners } from '../stores/ui'

export async function listAdminImages(query: ImageQuery): Promise<PageData<ImageVO>> {
  const page = await get<PageData<ImageVO>>('/admin/image/list', query)
  rememberOwners(page.records ?? [])
  return page
}

export function reviewImage(payload: {
  id: Id
  reviewStatus: 1 | 2
  reviewMessage?: string
}): Promise<boolean> {
  return post<boolean>('/admin/image/review', payload)
}

export function adminDeleteImage(id: Id): Promise<boolean> {
  return del<boolean>(`/admin/image/${id}`)
}