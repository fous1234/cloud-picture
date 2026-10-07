import type { ImageMeta } from './image'
import type { Id, PageData, SpaceImageQuery, SpaceImageVO, SpaceVO } from './types'
import { del, get, post, postForm, put } from './http'

/** 当前用户的私有空间；未创建时返回 null */
export function getMySpace(): Promise<SpaceVO | null> {
  return get<SpaceVO | null>('/space')
}

export function createSpace(name?: string): Promise<SpaceVO> {
  return post<SpaceVO>('/space', name ? { name } : {})
}

export function renameSpace(name: string): Promise<boolean> {
  return put<boolean>('/space', { name })
}

/** 删除自己的空间并级联删除其中全部图片与对象存储文件 */
export function deleteSpace(): Promise<boolean> {
  return del<boolean>('/space')
}

/** 私有图上传：免审核，元数据与文件同请求提交 */
export function uploadSpaceImage(
  file: File,
  meta: ImageMeta,
  onProgress?: (percent: number) => void,
): Promise<SpaceImageVO> {
  const form = new FormData()
  form.append('file', file)
  if (meta.name) form.append('name', meta.name)
  if (meta.introduction) form.append('introduction', meta.introduction)
  if (meta.category) form.append('category', meta.category)
  meta.tags.forEach((tag) => form.append('tags', tag))
  return postForm<SpaceImageVO>('/space/image/upload', form, onProgress)
}

export function listSpaceImages(query: SpaceImageQuery): Promise<PageData<SpaceImageVO>> {
  return get<PageData<SpaceImageVO>>('/space/image/list', query)
}

export function downloadSpaceImage(id: Id): Promise<string> {
  return get<string>(`/space/image/${id}/download`)
}

export function deleteSpaceImage(id: Id): Promise<boolean> {
  return del<boolean>(`/space/image/${id}`)
}