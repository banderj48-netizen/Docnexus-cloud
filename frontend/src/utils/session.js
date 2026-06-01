/**
 * 浏览器登录会话工具。
 *
 * accessToken 适合短期鉴权，refreshToken + sessionId 才是浏览器关闭后继续接回原会话的凭据。
 * 因此路由守卫和请求拦截器都应复用这里的续签逻辑，避免重复创建 user_session。
 */
import axios from 'axios'
import { STORAGE_KEYS } from '../constants'
import { isTokenValid } from './jwt'

const AUTH_MESSAGE_KEY = 'docnexusAuthMessage'
const SAME_DEVICE_LOGIN_MESSAGE = '已在相同设备的其他地方登录'
const LOGIN_REQUIRED_MESSAGE = '请先登录；如果该设备已存在会话，登录后会自动接管原会话'

/**
 * 清理本地登录态，仅在用户主动退出或 refreshToken 确认失效时调用。
 */
export function clearAuthState() {
  localStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN)
  localStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN)
  localStorage.removeItem(STORAGE_KEYS.SESSION_ID)
  localStorage.removeItem(STORAGE_KEYS.USER_INFO)
  localStorage.removeItem('userId')
  localStorage.removeItem('userName')
}

export function rememberAuthMessage(message) {
  if (message) {
    sessionStorage.setItem(AUTH_MESSAGE_KEY, message)
  }
}

export function consumeAuthMessage() {
  const message = sessionStorage.getItem(AUTH_MESSAGE_KEY)
  if (message) {
    sessionStorage.removeItem(AUTH_MESSAGE_KEY)
  }
  return message
}

/**
 * 从 axios 异常中提取后端业务错误文案。
 *
 * 旧浏览器被同设备新登录接管时，后端会返回明确的业务提示；
 * 这里优先读取 response.data.message，避免只展示 axios 的通用错误。
 */
function resolveAuthErrorMessage(error, fallback = SAME_DEVICE_LOGIN_MESSAGE) {
  return error?.response?.data?.message
    || error?.response?.data?.error
    || error?.message
    || fallback
}

/**
 * 使用当前浏览器保存的 refreshToken 和 sessionId 续签 accessToken。
 */
export async function refreshAccessToken() {
  const refreshToken = localStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN)
  const sessionId = localStorage.getItem(STORAGE_KEYS.SESSION_ID)

  if (!refreshToken || !sessionId) {
    throw new Error('缺少 refreshToken 或 sessionId')
  }

  let response
  try {
    response = await axios.post('/api/auth/refresh', {
      sessionId,
      refreshToken,
    })
  } catch (error) {
    throw new Error(resolveAuthErrorMessage(error))
  }

  const body = response.data || {}
  if (body.code !== 200 && body.code !== 0) {
    throw new Error(body.message || '刷新登录态失败')
  }

  const data = body.data || {}
  if (!data.token || !data.refreshToken || !data.sessionId) {
    throw new Error('刷新登录态返回数据不完整')
  }

  localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, data.token)
  localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, data.refreshToken)
  localStorage.setItem(STORAGE_KEYS.SESSION_ID, data.sessionId)

  let currentUser = {}
  try {
    currentUser = JSON.parse(localStorage.getItem(STORAGE_KEYS.USER_INFO) || '{}')
  } catch {
    currentUser = {}
  }

  const nextUser = {
    ...currentUser,
    id: data.userId || currentUser.id,
    userId: data.userId || currentUser.userId,
    username: data.username || currentUser.username,
    role: data.role || currentUser.role,
  }

  localStorage.setItem(STORAGE_KEYS.USER_INFO, JSON.stringify(nextUser))
  if (nextUser.id || nextUser.userId) {
    localStorage.setItem('userId', String(nextUser.id || nextUser.userId))
  }
  if (nextUser.username) {
    localStorage.setItem('userName', nextUser.username)
  }

  return data.token
}

async function verifyAccessTokenWithServer(token, sessionId) {
  await axios.post('/api/users/me/sessions/heartbeat', {
    sessionId,
  }, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  })
}

/**
 * 确保当前浏览器拥有可用登录态。
 *
 * 如果 accessToken 仍有效，直接放行；如果 accessToken 已过期但 refreshToken 仍可用，
 * 会先续签并继续使用同一个 sessionId，从而避免重新登录生成新会话。
 */
export async function ensureAuthenticatedSession(options = {}) {
  const verifyServer = options.verifyServer === true
  const rememberMissing = options.rememberMissing !== false
  const token = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN)
  if (isTokenValid(token)) {
    if (!verifyServer) {
      return true
    }
    const sessionId = localStorage.getItem(STORAGE_KEYS.SESSION_ID)
    if (!sessionId) {
      clearAuthState()
      if (rememberMissing) rememberAuthMessage(LOGIN_REQUIRED_MESSAGE)
      return false
    }
    try {
      await verifyAccessTokenWithServer(token, sessionId)
      return true
    } catch {
      try {
        await refreshAccessToken()
        return true
      } catch (refreshError) {
        clearAuthState()
        rememberAuthMessage(resolveAuthErrorMessage(refreshError))
        return false
      }
    }
  }

  const refreshToken = localStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN)
  const sessionId = localStorage.getItem(STORAGE_KEYS.SESSION_ID)
  if (!refreshToken || !sessionId) {
    clearAuthState()
    if (rememberMissing) rememberAuthMessage(token ? SAME_DEVICE_LOGIN_MESSAGE : LOGIN_REQUIRED_MESSAGE)
    return false
  }

  try {
    await refreshAccessToken()
    return true
  } catch (error) {
    clearAuthState()
    rememberAuthMessage(resolveAuthErrorMessage(error))
    return false
  }
}
