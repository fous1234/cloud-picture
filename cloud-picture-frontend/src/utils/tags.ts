/** t_image.tags 是逗号分隔的 varchar(512)，前端按同一上限做预检 */
export const TAGS_MAX_LENGTH = 512

export function normalizeTags(tags: string[]): string[] {
  const result: string[] = []
  for (const raw of tags) {
    const tag = (raw ?? '').replace(/[,，]/g, '').trim()
    if (tag && !result.includes(tag)) result.push(tag)
  }
  return result
}

export function tagsTooLong(tags: string[]): boolean {
  return tags.join(',').length > TAGS_MAX_LENGTH
}

export const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']
export const ALLOWED_IMAGE_EXTENSIONS = ['jpg', 'jpeg', 'png', 'webp']

/** 默认 10 MB，与后端 picture.upload.max-file-size 保持一致 */
export const MAX_UPLOAD_BYTES = 10 * 1024 * 1024

export function validateImageFile(file: File): string | null {
  const extension = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : ''
  if (!ALLOWED_IMAGE_TYPES.includes(file.type) && !ALLOWED_IMAGE_EXTENSIONS.includes(extension)) {
    return '仅支持 JPEG、PNG 或 WebP 格式'
  }
  if (file.size > MAX_UPLOAD_BYTES) {
    return `图片不能超过 ${MAX_UPLOAD_BYTES / 1024 / 1024} MB`
  }
  return null
}