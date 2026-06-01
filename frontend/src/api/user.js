/**
 * 用户模块 API 接口。
 */
import request from '../utils/request'

const encodeBase64 = (value) => {
  const bytes = new TextEncoder().encode(value || '')
  let binary = ''
  bytes.forEach(byte => {
    binary += String.fromCharCode(byte)
  })
  return btoa(binary)
}

const getDeviceName = () => {
  const platform = navigator?.userAgentData?.platform || navigator?.platform || 'Unknown'
  if (/win/i.test(platform)) return 'Windows'
  if (/mac/i.test(platform)) return 'macOS'
  if (/iphone/i.test(platform)) return 'iPhone'
  if (/ipad/i.test(platform)) return 'iPad'
  if (/android/i.test(platform)) return 'Android'
  if (/linux/i.test(platform)) return 'Linux'
  return platform
}

/**
 * 构建设备级指纹。
 *
 * 该指纹用于后端计算 deviceId，只保留跨浏览器稳定的设备特征；
 * 不包含浏览器名称、浏览器版本、User-Agent 和 localStorage UUID，
 * 避免同一台电脑上的 Edge / Chrome 被识别成两台设备。
 */
const buildDeviceFingerprint = () => {
  const screenInfo = window?.screen || {}
  const platform = getDeviceName()
  return JSON.stringify({
    schema: 'device-v2',
    platform,
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone || '',
    screen: `${screenInfo.width || 0}x${screenInfo.height || 0}x${screenInfo.colorDepth || 0}`,
    hardwareConcurrency: navigator?.hardwareConcurrency || 0,
    maxTouchPoints: navigator?.maxTouchPoints || 0,
  })
}

export const userApi = {
  login: (data) => request.post('/auth/login', {
    username: data.username,
    password: encodeBase64(data.password),
    deviceFingerprint: buildDeviceFingerprint(),
    deviceName: getDeviceName(),
  }),

  register: (data) => request.post('/auth/register', {
    username: data.username,
    password: encodeBase64(data.password),
    confirmPassword: encodeBase64(data.confirmPassword),
    email: data.email,
    phone: data.phone,
    role: 'USER',
  }),

  verifyPasswordRecovery: (data) => request.post('/auth/password/recovery/verify', {
    username: data.username,
    email: encodeBase64(data.email),
    phone: encodeBase64(data.phone),
  }),

  resetPassword: (data) => request.post('/auth/password/recovery/reset', {
    username: data.username,
    resetToken: data.resetToken,
    password: encodeBase64(data.password),
    confirmPassword: encodeBase64(data.confirmPassword),
  }),

  logout: () => request.post('/auth/logout', null, {
    silent: true,
    skipAuthRefresh: true,
  }),

  refreshToken: (data) => request.post('/auth/refresh', {
    sessionId: data.sessionId,
    refreshToken: data.refreshToken,
  }),

  getUserInfo: (id) => request.get(`/users/${id}`),

  getCurrentProfile: () => request.get('/users/me'),

  updateCurrentProfile: (data) => request.put('/users/me/profile', {
    email: data.email,
    phone: data.phone,
  }),

  // 仅保留给主动安全清理场景；页面切换和刷新不再调用，避免账号中心反复回源 MySQL。
  clearCurrentProfileCache: () => request.delete('/users/me/profile-cache'),

  listCurrentSessions: (params = {}) => request.get('/users/me/sessions', {
    params: {
      currentSessionId: params.currentSessionId,
      pageNum: params.pageNum,
      pageSize: params.pageSize,
    },
  }),

  heartbeatCurrentSession: (sessionId) => request.post('/users/me/sessions/heartbeat', {
    sessionId,
  }, { silent: true }),

  logoutUserSession: (sessionId) => request.delete(`/users/me/sessions/${sessionId}`),

  changeCurrentPassword: (data) => request.put('/users/me/password', {
    oldPassword: encodeBase64(data.oldPassword),
    newPassword: encodeBase64(data.newPassword),
    confirmPassword: encodeBase64(data.confirmPassword),
  }),
}
