import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 后端网关绑定 8080，前端不配置 CORS，统一走 dev proxy；请求路径保留 /api 前缀（网关 StripPrefix=1）
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})