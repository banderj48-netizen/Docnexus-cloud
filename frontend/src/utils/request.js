/**
 * Axios 全局请求封装工具。
 */
import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { STORAGE_KEYS } from '../constants'
import { clearAuthState, refreshAccessToken, rememberAuthMessage } from './session'
import { createStaticDemoAdapter, isImplementedBackendRequest, isStaticDemoEnabled } from '../mock/staticDemo'

const service = axios.create({
  baseURL: '/api',
  timeout: 300000,
})

let refreshing = false
let waitingQueue = []
let authFailureNotified = false

const AI_PATH_PREFIX = '/ai/'
const AIOPS_PATH_PREFIX = '/ai/aiops/'
const SAME_DEVICE_LOGIN_MESSAGE = '已在相同设备的其他地方登录'

function resolveWaitingQueue(error, token) {
  waitingQueue.forEach(({ resolve, reject }) => {
    if (error) {
      reject(error)
    } else {
      resolve(token)
    }
  })
  waitingQueue = []
}

function isAiRequest(url) {
  return url && url.startsWith(AI_PATH_PREFIX) && !url.startsWith(AIOPS_PATH_PREFIX)
}

function notifyAuthFailure(message) {
  const text = message || '登录状态已失效，请重新登录'
  if (!authFailureNotified) {
    authFailureNotified = true
    rememberAuthMessage(text)
    window.setTimeout(() => {
      authFailureNotified = false
    }, 1500)
  }
}

/**
 * 判断登录失效提示是否需要展示。
 *
 * heartbeat 默认是静默请求，但同设备接管属于安全提示，
 * 即使来自静默请求，也要保存到登录页展示给旧浏览器用户。
 */
function shouldNotifyAuthFailure(config, message) {
  return !config?.silent || String(message || '').includes(SAME_DEVICE_LOGIN_MESSAGE)
}

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
    if (isStaticDemoEnabled() && !isImplementedBackendRequest(config)) {
      config.adapter = createStaticDemoAdapter
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
    if (!response.config?.silent) {
      ElMessage.error(res.message || '操作失败')
    }
    return Promise.reject(new Error(res.message || 'Error'))
  },
  async error => {
    if (error.config) {
      reportAiMetrics(error.config, true)
    }

    if (error.response && error.response.status === 401) {
      const originalRequest = error.config || {}

      if (originalRequest.skipAuthRefresh) {
        return Promise.reject(error)
      }

      if (originalRequest.url === '/auth/refresh' || originalRequest._retry) {
        const message = error.response?.data?.message || '登录状态已失效，请重新登录'
        if (shouldNotifyAuthFailure(originalRequest, message)) {
          notifyAuthFailure(message)
        }
        clearAuthState()
        router.replace('/login')
        return Promise.reject(error)
      }

      originalRequest._retry = true

      if (refreshing) {
        return new Promise((resolve, reject) => {
          waitingQueue.push({ resolve, reject })
        }).then((token) => {
          originalRequest.headers = originalRequest.headers || {}
          originalRequest.headers.Authorization = `Bearer ${token}`
          return service(originalRequest)
        })
      }

      refreshing = true

      try {
        const newToken = await refreshAccessToken()
        resolveWaitingQueue(null, newToken)
        originalRequest.headers = originalRequest.headers || {}
        originalRequest.headers.Authorization = `Bearer ${newToken}`
        return service(originalRequest)
      } catch (refreshError) {
        resolveWaitingQueue(refreshError, null)
        const message = refreshError?.message || '登录状态已失效，请重新登录'
        if (shouldNotifyAuthFailure(originalRequest, message)) {
          notifyAuthFailure(message)
        }
        clearAuthState()
        router.replace('/login')
        return Promise.reject(refreshError)
      } finally {
        refreshing = false
      }
    } else {
      const message = error.response?.data?.message || error.response?.data?.error || error.message
      if (!error.config?.silent) {
        ElMessage.error(message || '服务器开了小差，请稍后再试')
      }
    }

    return Promise.reject(error)
  }
)

export default service
