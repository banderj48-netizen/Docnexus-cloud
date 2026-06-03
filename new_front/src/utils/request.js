/**
 * @description DocAI-main 原始 Axios 封装，静态演示模式下由本地 Mock 接管后端请求。
 */
import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { STORAGE_KEYS } from '../constants'
import { createOriginalDemoAdapter, isOriginalDemoEnabled } from '../mock/originalDocAiDemo'

const service = axios.create({
  baseURL: '/api',
  timeout: 300000,
})

const AI_PATH_PREFIX = '/ai/'
const AIOPS_PATH_PREFIX = '/ai/aiops/'

/**
 * 判断是否为普通 AI 请求，AI Ops 指标接口不重复上报。
 */
function isAiRequest(url) {
  return url && url.startsWith(AI_PATH_PREFIX) && !url.startsWith(AIOPS_PATH_PREFIX)
}

/**
 * 上报原项目已有的 AI 调用指标；静态模式下也会被 Mock 接口接住。
 */
function reportAiMetrics(config, hasError) {
  const url = config.url || ''
  if (!isAiRequest(url)) return

  const startTime = config._startTime
  const duration = startTime ? Date.now() - startTime : 0
  const baseURL = config.baseURL || '/api'

  axios.post(baseURL + '/ai/aiops/metrics/counter', null, {
    params: { name: 'ai.requests', delta: 1 },
  }).catch(() => {})

  if (duration > 0) {
    axios.post(baseURL + '/ai/aiops/metrics/timer', null, {
      params: { name: 'ai.request', duration },
    }).catch(() => {})
  }

  if (hasError) {
    axios.post(baseURL + '/ai/aiops/metrics/counter', null, {
      params: { name: 'ai.errors', delta: 1 },
    }).catch(() => {})
  }
}

service.interceptors.request.use(
  config => {
    config._startTime = Date.now()
    if (isOriginalDemoEnabled()) {
      config.adapter = createOriginalDemoAdapter
    }

    const token = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN)
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

service.interceptors.response.use(
  response => {
    if (response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') {
      reportAiMetrics(response.config, false)
      return response.data
    }

    const res = response.data
    if (res && res.code === undefined) {
      reportAiMetrics(response.config, false)
      return { data: res, code: 200, message: 'success' }
    }

    if (res.code === 200 || res.code === 0) {
      reportAiMetrics(response.config, false)
      return res
    }

    reportAiMetrics(response.config, true)
    ElMessage.error(res.message || '操作失败')
    return Promise.reject(new Error(res.message || 'Error'))
  },
  error => {
    if (error.config) {
      reportAiMetrics(error.config, true)
    }

    if (error.response && error.response.status === 401) {
      ElMessage.error('登录已过期，请重新登录')
      localStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN)
      router.push('/login')
    } else {
      ElMessage.error('服务器开了小差，请稍后再试')
    }
    return Promise.reject(error)
  }
)

export default service
