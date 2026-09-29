import axios, { AxiosError } from 'axios'
import { message } from 'ant-design-vue'
import type { ApiResponse } from './types'
import { clearSession, session } from '../stores/session'

export class ApiError extends Error {
  code: string
  status: number

  constructor(code: string, msg: string, status: number) {
    super(msg)
    this.code = code
    this.status = status
  }
}

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
})

// 后端把雪花 ID 序列化为 19 位 JSON 数字，超出 JS 安全整数范围会在 JSON.parse 时丢精度，
// 这里在解析前给“值位置上的长整数”补引号，使 ID 以字符串形式进入业务代码。
// ponytail: 只覆盖 16 位以上的整数；若后端改为字符串序列化，本段可直接删除。
const LONG_INT_VALUE = /([:,[\s]*)(\d{16,})(?=\s*[,}\]])/g

http.defaults.transformResponse = [
  (data: unknown) => {
    if (typeof data !== 'string' || data === '') return data
    try {
      return JSON.parse(data.replace(LONG_INT_VALUE, '$1"$2"'))
    } catch {
      return data
    }
  },
]

http.interceptors.request.use((config) => {
  if (session.token) config.headers.Authorization = `Bearer ${session.token}`
  return config
})

let onUnauthorized: (() => void) | null = null

/** 401 时清会话并跳回登录页，由 router 注册，避免 http 层反向依赖 router */
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiResponse<unknown>>) => {
    const status = error.response?.status ?? 0
    const body = error.response?.data
    const code = body?.code || (status ? `HTTP_${status}` : 'NETWORK_ERROR')
    const msg =
      body?.message ||
      (status ? `请求失败（HTTP ${status}）` : '无法连接后端服务，请确认网关已启动')
    if (status === 401) {
      clearSession()
      onUnauthorized?.()
    } else if (status === 403) {
      // 403 统一提示，页面自身仍可展示无权限占位
      message.error(msg)
    }
    return Promise.reject(new ApiError(code, msg, status))
  },
)

export async function get<T>(url: string, params?: object): Promise<T> {
  const res = await http.get<ApiResponse<T>>(url, { params })
  return res.data.data
}

export async function post<T>(url: string, body?: object): Promise<T> {
  const res = await http.post<ApiResponse<T>>(url, body)
  return res.data.data
}

export async function put<T>(url: string, body?: object): Promise<T> {
  const res = await http.put<ApiResponse<T>>(url, body)
  return res.data.data
}

export async function patch<T>(url: string, body?: object): Promise<T> {
  const res = await http.patch<ApiResponse<T>>(url, body)
  return res.data.data
}

export async function del<T>(url: string): Promise<T> {
  const res = await http.delete<ApiResponse<T>>(url)
  return res.data.data
}

export async function postForm<T>(
  url: string,
  form: FormData,
  onProgress?: (percent: number) => void,
): Promise<T> {
  const res = await http.post<ApiResponse<T>>(url, form, {
    onUploadProgress: (event) => {
      if (event.total) onProgress?.(Math.round((event.loaded * 100) / event.total))
    },
  })
  return res.data.data
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof Error) return error.message
  return '操作失败，请稍后重试'
}