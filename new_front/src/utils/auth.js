import { userApi } from '../api/user'
import { clearAuthState } from './session'

/**
 * 清理本地登录态。
 */
export { clearAuthState }

/**
 * 调用后端退出接口，并清理前端缓存。
 */
export async function logout() {
  try {
    await userApi.logout()
  } finally {
    clearAuthState()
  }
}
