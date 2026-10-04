import { postForm } from './http'

export interface AiImageMetadata {
  introduction: string
  tags: string[]
}

export function generateImageMetadata(file: File): Promise<AiImageMetadata> {
  const form = new FormData()
  form.append('file', file)
  return postForm<AiImageMetadata>('/ai/image-metadata', form)
}