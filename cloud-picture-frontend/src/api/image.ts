import type { Id, ImageQuery, ImageShareVO, ImageVO, PageData, SharedImageVO } from './types'
import { del, get, post, postForm, put } from './http'
import { rememberOwners } from '../stores/ui'

export interface ImageMeta {
  name?: string
  introduction?: string
  category?: string
  tags: string[]
}

export async function listImages(query: ImageQuery): Promise<PageData<ImageVO>> {
  const page = await get<PageData<ImageVO>>('/image/list', query)
  rememberOwners(page.records ?? [])
  return page
}

export function listTags(limit = 20): Promise<string[]> {
  return get<string[]>('/image/tags', { limit })
}

export function getImage(id: Id): Promise<ImageVO> {
  return get<ImageVO>(`/image/${id}`)
}

export function downloadImage(id: Id): Promise<string> {
  return get<string>(`/image/${id}/download`)
}

export function getImageShare(id: Id): Promise<ImageShareVO> {
  return get<ImageShareVO>(`/image/${id}/share`)
}

export function createImageShare(id: Id): Promise<ImageShareVO> {
  return post<ImageShareVO>(`/image/${id}/share`)
}

export function revokeImageShare(id: Id): Promise<boolean> {
  return del<boolean>(`/image/${id}/share`)
}

export function getSharedImage(token: string): Promise<SharedImageVO> {
  return get<SharedImageVO>(`/image/share/${encodeURIComponent(token)}`)
}

export function updateImage(payload: { id: Id } & ImageMeta): Promise<boolean> {
  return put<boolean>('/image', payload)
}

export function deleteImage(id: Id): Promise<boolean> {
  return del<boolean>(`/image/${id}`)
}

/** 单张上传：文件走 multipart，元数据作为同请求的表单字段 */
export function uploadImage(
  file: File,
  meta: ImageMeta,
  onProgress?: (percent: number) => void,
): Promise<ImageVO> {
  const form = new FormData()
  form.append('file', file)
  if (meta.name) form.append('name', meta.name)
  if (meta.introduction) form.append('introduction', meta.introduction)
  if (meta.category) form.append('category', meta.category)
  meta.tags.forEach((tag) => form.append('tags', tag))
  return postForm<ImageVO>('/image/upload', form, onProgress)
}

// 「我的上传」依赖后端新增按当前用户过滤的分页接口，后端当前未实现该端点；
// 这里只按 PRD 给出的候选契约调用，不做 ownerId 过滤、也不用共享图库列表冒充结果。
export function listMyImages(query: ImageQuery): Promise<PageData<ImageVO>> {
  return get<PageData<ImageVO>>('/image/mine', query)
}
