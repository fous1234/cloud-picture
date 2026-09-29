import type { Id, PageData, Role, UserStatus, UserVO } from './types'
import { get, patch, post, put } from './http'

export interface LoginPayload {
  userAccount: string
  userPassword: string
}

export interface RegisterPayload {
  userAccount: string
  userPassword: string
  checkPassword: string
  userName?: string
}

export interface LoginResult {
  id: Id
  account: string
  name: string | null
  avatar: string | null
  role: Role
  token: string
}

export function login(payload: LoginPayload): Promise<LoginResult> {
  return post<LoginResult>('/auth/login', payload)
}

export function register(payload: RegisterPayload): Promise<Id> {
  return post<Id>('/auth/register', payload)
}

export function logout(): Promise<boolean> {
  return post<boolean>('/auth/logout')
}

export function getCurrentUser(): Promise<UserVO> {
  return get<UserVO>('/user/me')
}

export function updateCurrentUser(payload: {
  userName?: string
  userProfile?: string
}): Promise<boolean> {
  return put<boolean>('/user/me', payload)
}

export interface UserQuery {
  current: number
  size: number
  account?: string
  name?: string
}

export function listUsers(query: UserQuery): Promise<PageData<UserVO>> {
  return get<PageData<UserVO>>('/admin/user/list', query)
}

export function updateUserStatus(id: Id, status: UserStatus): Promise<boolean> {
  return patch<boolean>(`/admin/user/${id}/status`, { status })
}