import { STORAGE_KEYS } from '../constants'

const now = Date.now()

let todos = [
  { id: 'todo_001', title: '提炼招标文件关键要求', instruction: '总结评分标准、交付范围和风险条款', detailInstruction: '重点关注验收标准', type: 'summary', priority: 'high', status: 'running', sources: '招标文件.pdf', createdAt: '06/03 09:30' },
  { id: 'todo_002', title: '生成客户培训大纲', instruction: '基于客户培训手册生成 8 页 PPT 大纲', detailInstruction: '', type: 'ppt', priority: 'normal', status: 'pending', sources: '客户培训手册.docx', createdAt: '06/02 16:10' },
]

const agentTasks = [
  { traceId: 'trace_001', title: '资料解析与切片', status: 'SUCCESS', startedAt: '06/03 09:31', duration: '2分8秒', steps: [{ id: 's1', name: '下载原文', status: 'SUCCESS' }, { id: 's2', name: '语义切片', status: 'SUCCESS' }, { id: 's3', name: '写入索引', status: 'SUCCESS' }] },
  { traceId: 'trace_002', title: 'PPT 大纲生成', status: 'RUNNING', startedAt: '06/03 10:12', duration: '38秒', steps: [{ id: 's1', name: '读取资料', status: 'SUCCESS' }, { id: 's2', name: '生成结构', status: 'RUNNING' }] },
]

const chatWindows = [
  { id: 'conv_001', title: '招标文件问答', updatedAt: '06/03 10:20' },
  { id: 'conv_002', title: '培训资料学习', updatedAt: '06/02 17:40' },
]

function page(records, pageNum = 1, pageSize = 10) {
  const safePageNum = Math.max(1, Number(pageNum) || 1)
  const safePageSize = Number(pageSize) || 10
  const start = (safePageNum - 1) * safePageSize
  return {
    records: records.slice(start, start + safePageSize),
    total: records.length,
    pageNum: safePageNum,
    pageSize: safePageSize,
    pages: Math.max(1, Math.ceil(records.length / safePageSize)),
  }
}

function success(data, message = 'success') {
  return { code: 200, message, data }
}

export function isStaticDemoEnabled() {
  return import.meta.env.VITE_STATIC_DEMO === 'true' || localStorage.getItem('docnexus_static_demo') === 'true'
}

export function ensureStaticDemoSession() {
  // 已实现后端的认证链路不再注入静态登录态，避免不存在账号绕过后端校验。
}

/**
 * 清理历史静态演示登录态，避免旧 demo token 继续绕过真实后端登录。
 */
export function clearLegacyStaticDemoSession() {
  if (localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN) !== 'static-demo-token') return
  localStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN)
  localStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN)
  localStorage.removeItem(STORAGE_KEYS.SESSION_ID)
  localStorage.removeItem(STORAGE_KEYS.USER_INFO)
  localStorage.removeItem('userId')
  localStorage.removeItem('userName')
}

export function isImplementedBackendRequest(config = {}) {
  const url = String(config.url || '').replace(/^\/api/, '')
  return [
    '/auth/',
    '/users/',
    '/files/',
    '/logs/',
  ].some(prefix => url.startsWith(prefix))
}

function blobResponse(config) {
  const blob = new Blob(['DocNexus Cloud 静态演示文件内容'], { type: 'text/plain;charset=utf-8' })
  return { data: blob, status: 200, statusText: 'OK', headers: {}, config }
}

function route(config) {
  const method = String(config.method || 'get').toLowerCase()
  const url = String(config.url || '').replace(/^\/api/, '')

  if (isImplementedBackendRequest(config)) {
    return { code: 501, message: '该接口已接入后端，不允许使用静态演示数据', data: null }
  }

  if (config.responseType === 'blob' || config.responseType === 'arraybuffer') return blobResponse(config)

  if (url.startsWith('/documents')) return success(page([{ id: 'doc_001', title: '招标文件解析稿', summary: '项目范围、评分办法、交付风险已提取。', version: 3, status: 'active', category: '招投标', tags: ['招标', '风险'], updateTime: '06/03 10:00' }]).records)
  if (url === '/factory/steps') return success([{ id: 'step_1', name: '资料理解', status: 'done' }, { id: 'step_2', name: '生成初稿', status: 'running' }, { id: 'step_3', name: '引用审阅', status: 'pending' }])

  if (url === '/ai/todos') return success(todos)
  if (url.startsWith('/ai/todos/') && url.endsWith('/steps')) return success([{ time: '10:12', title: '读取资料', detail: '已加载 3 个来源文档', status: 'done' }, { time: '10:13', title: '生成摘要', detail: '正在整理关键观点', status: 'running' }])
  if (url === '/ai/todos' && method === 'post') {
    const todo = { id: `todo_${Date.now()}`, status: 'pending', createdAt: '刚刚', ...(config.data || {}) }
    todos.unshift(todo)
    return success(todo)
  }
  if (url.includes('/run')) return success({ ...(todos[0] || {}), status: 'running' })
  if (url.startsWith('/ai/todos/')) return success(config.data || null)

  if (url === '/agent/tasks') return success(agentTasks)
  if (url.startsWith('/agent/tasks/')) return success(agentTasks[0])
  if (url.startsWith('/ai/aiops/monitor')) return success({ 'ai.request.stats': { count: 128, avg: 860, p95: 1800 }, 'ai.errors': 2 })
  if (url.startsWith('/ai/aiops/health')) return success({ status: 'UP', models: ['mock-qwen', 'mock-gpt'], redis: 'UP', minio: 'UP' })
  if (url.startsWith('/ai/aiops/faults')) return success([{ id: 'fault_001', level: 'LOW', title: '静态演示告警', resolved: false }])
  if (url.startsWith('/ai/aiops')) return success({})
  if (url.startsWith('/ai/')) return success({ answer: '这是静态演示模式下的 AI 回复。', content: '静态演示结果', keywords: ['知识库', '文档', 'AI'] })

  if (url === '/study/chat/windows') return success(chatWindows)
  if (url === '/study/chat/start') return success({ id: `conv_${Date.now()}`, title: '新的学习对话' })
  if (url.includes('/study/chat/') && url.endsWith('/history')) return success([{ role: 'assistant', content: '欢迎进入静态学习室，可以围绕上传资料提问。' }])
  if (url === '/study/chat') return success({ role: 'assistant', content: '这是基于静态知识库的模拟回答，后端接入后会替换为真实 RAG。' })
  if (url.startsWith('/study/chat/')) return success({})

  if (url.startsWith('/knowledge-library')) return success({})
  return success({})
}

export function createStaticDemoAdapter(config) {
  return new Promise((resolve) => {
    window.setTimeout(() => {
      const result = route(config)
      resolve(result.status ? result : { data: result, status: 200, statusText: 'OK', headers: {}, config })
    }, 120)
  })
}
