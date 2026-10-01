import type { ApiResponse } from './types'
import { http } from './http'

export interface ImageImportRequest {
  keyword: string
  count: number
  category?: string
  tags?: string[]
}

export interface ImportResult {
  imported: number
  skipped: number
  failed: number
}

export async function importPexelsImages(payload: ImageImportRequest): Promise<ImportResult> {
  const response = await http.post<ApiResponse<ImportResult>>('/admin/image/import', payload, {
    timeout: 240_000,
  })
  return response.data.data
}
