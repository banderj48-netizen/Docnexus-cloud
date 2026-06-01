import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发环境默认代理到本项目 Spring Boot 后端端口，生产环境由 Nginx 负责反向代理。
const apiTarget = process.env.VITE_API_TARGET || 'http://localhost:8088'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: false,
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.removeHeader('Origin')
            proxyReq.setHeader('Origin', 'http://localhost:5173')
            proxyReq.setHeader('Connection', 'keep-alive')
          })
          proxy.on('error', (err) => {
            console.log('[proxy error]', err.message)
          })
        }
      }
    }
  }
})
