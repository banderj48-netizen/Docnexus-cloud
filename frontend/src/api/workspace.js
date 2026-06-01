/**
 * 工作台页面 API。
 */
import request from '../utils/request'

export const workspaceApi = {
  // 查询真实文件元数据列表，空数组时前端展示空状态。
  listKnowledgeFiles: () => request.get('/files/list'),

  // 查询知识处理步骤。接口路径沿用 /factory/steps，避免破坏旧后端契约。
  listFactorySteps: () => request.get('/factory/steps'),

  // 查询当前用户 AI 日志。后端会根据 JWT 限制普通用户只能看到自己的任务快照。
  listAiLogs: () => request.get('/agent/tasks'),
}
