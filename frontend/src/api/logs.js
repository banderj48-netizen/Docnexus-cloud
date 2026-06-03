import request from '../utils/request'

/**
 * 日志服务接口。
 *
 * 普通用户页面只使用 userOperationSummary/listUserOperations；网关审计和安全告警接口保留给后续管理员后台。
 */
export const logsApi = {
  /**
   * 查询当前用户最近 5 天主动业务操作统计。
   */
  userOperationSummary(config = {}) {
    return request.get('/logs/user-operations/summary', config)
  },

  /**
   * 删除当前用户的用户操作 Redis 缓存，新的主动业务操作发生时静默调用。
   */
  invalidateUserOperationCache(config = {}) {
    return request.delete('/logs/user-operations/cache', { silent: true, ...config })
  },

  /**
   * 分页查询当前用户最近 5 天主动业务操作日志。
   */
  listUserOperations(params) {
    return request.get('/logs/user-operations', { params })
  },

  /**
   * 查询管理员日志概览，后续管理员后台使用。
   */
  summary() {
    return request.get('/logs/summary')
  },

  /**
   * 分页查询网关请求审计日志，后续管理员后台使用。
   */
  listGatewayAudits(params) {
    return request.get('/logs/gateway-audits', { params })
  },

  /**
   * 分页查询安全告警日志，后续管理员后台使用。
   */
  listSecurityAlerts(params) {
    return request.get('/logs/security-alerts', { params })
  },

  /**
   * 标记安全告警已处理，后续管理员后台使用。
   */
  markSecurityAlertHandled(eventId) {
    return request.patch(`/logs/security-alerts/${eventId}/handled`)
  },
}
