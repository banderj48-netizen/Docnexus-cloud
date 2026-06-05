import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发环境配置说明：只暴露前端端口给 frp，后端仍通过 Vite 代理访问本机 Gateway。
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiTarget = env.VITE_API_TARGET || 'http://127.0.0.1:8088'
  const devHost = env.VITE_DEV_HOST || '0.0.0.0'
  const devPort = Number(env.VITE_DEV_PORT || 5173)

  return {
    plugins: [vue()],
    server: {
      host: devHost,
      port: devPort,
      strictPort: true,
      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: false,
          configure: (proxy) => {
            proxy.on('proxyReq', (proxyReq) => {
              // 代理请求统一模拟本地前端来源，避免后端 CORS 误判外部穿透域名。
              proxyReq.removeHeader('Origin')
              proxyReq.setHeader('Origin', `http://127.0.0.1:${devPort}`)
              proxyReq.setHeader('Connection', 'keep-alive')
            })
            proxy.on('error', (err) => {
              console.log('[proxy error]', err.message)
            })
          }
        }
      }
    }
  }
})
