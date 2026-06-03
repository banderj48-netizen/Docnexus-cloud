import request from '../utils/request'

/**
 * AI 日志系统接口。
 *
 * 当前后端 `/api/agent/tasks` 已经基于 JWT 中的 userId 查询当前用户任务快照；
 * 前端页面还会在展示前做一次 userId 过滤，避免未来接口扩展时误展示其他用户日志。
 */
export const agentLogApi = {
  listTasks() {
    return request.get('/agent/tasks')
  },
  getTask(traceId) {
    return request.get(`/agent/tasks/${traceId}`)
  },
}
