import { reactive } from 'vue'
import type { Role, UserVO } from '../api/types'

export interface SessionState {
  token: string
  user: UserVO | null
  /** 是否已至少拉取过一次 /api/user/me */
  hydrated: boolean
}

const TOKEN_KEY = 'cloud-picture.token'

export const session = reactive<SessionState>({
  token: localStorage.getItem(TOKEN_KEY) ?? '',
  user: null,
  hydrated: false,
})

export function setToken(token: string) {
  session.token = token
  localStorage.setItem(TOKEN_KEY, token)
}

export function setUser(user: UserVO | null) {
  session.user = user
  session.hydrated = true
}

export function clearSession() {
  session.token = ''
  session.user = null
  session.hydrated = true
  localStorage.removeItem(TOKEN_KEY)
}

export function isLoggedIn(): boolean {
  return !!session.token
}

export function role(): Role | null {
  return session.user?.role ?? null
}

export function isAdmin(): boolean {
  return session.user?.role === 'ADMIN'
}