/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 覆盖默认的 /api 前缀，默认由 Vite dev proxy 转发到网关 8080 */
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}