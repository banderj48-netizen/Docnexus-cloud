/**
 * 文档库与知识图谱模块 API。
 *
 * 当前页面数据仍为前端静态演示数据；本文件只预留后端接入点，
 * 后续把页面中的静态数组替换为这些方法即可完成联调。
 */
import request from '../utils/request'

export const knowledgeLibraryApi = {
  /**
   * 查询文档库首页统计卡片数据。
   */
  getOverview: () => request.get('/knowledge-library/overview'),

  /**
   * 查询上传与解析处理队列。
   * @param {Object} params 查询条件，包含 status/page/size。
   */
  listProcessingQueue: (params) => request.get('/files/processing-queue', { params }),

  /**
   * 查询已上传文档列表。
   * @param {Object} params 查询条件，包含 keyword/type/status/page/size。
   */
  listDocuments: (params) => request.get('/files/list', { params }),

  /**
   * 普通上传文档资料，适用于小于 5MB 的文件。
   * @param {FormData} formData 包含 file 的表单数据。
   */
  uploadDocument: (formData) => request.post('/files/upload', formData),

  /**
   * 初始化分片上传，适用于 5MB 到 200MB 的文件。
   * @param {Object} data 文件名、大小、类型、知识库等初始化参数。
   */
  initMultipartUpload: (data) => request.post('/files/multipart/init', data),

  /**
   * 上传单个文件分片，后端负责保存临时分片并记录进度。
   * @param {String} uploadId 上传会话 ID。
   * @param {FormData} formData 包含 chunkIndex/totalChunks/chunkFile 等字段。
   */
  uploadChunk: (uploadId, formData) => request.post(`/files/multipart/${uploadId}/chunks`, formData),

  /**
   * 通知后端合并分片并创建正式文件记录。
   * @param {String} uploadId 上传会话 ID。
   */
  completeMultipartUpload: (uploadId) => request.post(`/files/multipart/${uploadId}/complete`),

  /**
   * 取消上传，后端需要清理 Redis 上传状态和 MinIO 临时分片。
   * @param {String} uploadId 上传会话 ID。
   */
  cancelUpload: (uploadId) => request.delete(`/files/uploads/${uploadId}`),

  /**
   * 查询处理任务的最新进度，后端优先从 Redis 返回。
   * @param {String} taskId 处理任务 ID。
   */
  getTaskProgress: (taskId) => request.get(`/files/tasks/${taskId}`),

  /**
   * 取消或删除处理队列中的任务。
   * @param {String} taskId 处理任务 ID。
   */
  cancelProcessingTask: (taskId) => request.delete(`/files/tasks/${taskId}`),

  /**
   * 查询知识图谱概览节点、边和统计数据。
   */
  getGraphOverview: () => request.get('/knowledge-library/graph/overview'),

  /**
   * 查询 Agent 解析流水线状态。
   */
  getPipelineStatus: () => request.get('/knowledge-library/pipeline/status'),

  /**
   * 查询最近完成解析的文档。
   * @param {Object} params 查询条件，包含 limit。
   */
  listRecentParsedDocuments: (params) => request.get('/knowledge-library/recent-parsed', { params }),
}
