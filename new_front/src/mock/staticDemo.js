import { STORAGE_KEYS } from '../constants'

const now = Date.now()
const demoToken = 'static-demo-token'

const demoUser = {
  id: 1,
  userId: 1,
  username: 'demo',
  role: 'ADMIN',
  email: 'demo@docnexus.local',
  phone: '13800000000',
  accountStatus: 'ENABLE',
  accountStatusText: '正常',
  createTimeMillis: now - 1000 * 60 * 60 * 24 * 120,
  lastLoginAtMillis: now - 1000 * 60 * 8,
}

const demoFiles = [
  createFile('file_demo_001', '招标文件.pdf', 'PDF', 2867200, '2.7 MB', '06/03 09:20', 'PENDING', 'NONE', 0),
  createFile('file_demo_002', '客户培训手册.docx', 'W', 5144576, '4.9 MB', '06/02 15:36', 'PROCESSING', 'BUILDING', 46),
  createFile('file_demo_003', '产品路线图.pptx', 'P', 8283750, '7.9 MB', '06/01 18:05', 'SUCCESS', 'SUCCESS', 100),
]

let demoTodos = [
  {
    id: 'todo_001',
    title: '提炼招标文件关键要求',
    instruction: '总结评分标准、交付范围和风险条款',
    detailInstruction: '重点关注验收标准和付款条件',
    type: 'summary',
    priority: 'high',
    status: 'running',
    sources: '招标文件.pdf',
    createdAt: '06/03 09:30',
  },
  {
    id: 'todo_002',
    title: '生成客户培训大纲',
    instruction: '基于客户培训手册生成 8 页 PPT 大纲',
    detailInstruction: '',
    type: 'ppt',
    priority: 'normal',
    status: 'pending',
    sources: '客户培训手册.docx',
    createdAt: '06/02 16:10',
  },
]

const demoAgentTasks = [
  {
    traceId: 'trace_001',
    title: '资料解析与切片',
    status: 'SUCCESS',
    startedAt: '06/03 09:31',
    duration: '2分8秒',
    steps: [
      { id: 's1', name: '下载原文', status: 'SUCCESS' },
      { id: 's2', name: '语义切片', status: 'SUCCESS' },
      { id: 's3', name: '写入索引', status: 'SUCCESS' },
    ],
  },
  {
    traceId: 'trace_002',
    title: 'PPT 大纲生成',
    status: 'RUNNING',
    startedAt: '06/03 10:12',
    duration: '38秒',
    steps: [
      { id: 's1', name: '读取资料', status: 'SUCCESS' },
      { id: 's2', name: '生成结构', status: 'RUNNING' },
    ],
  },
]

const demoChatWindows = [
  { id: 'conv_001', title: '招标文件问答', updatedAt: '06/03 10:20' },
  { id: 'conv_002', title: '培训资料学习', updatedAt: '06/02 17:40' },
]

/**
 * 创建文件列表演示数据，字段尽量覆盖新旧页面共同使用的展示属性。
 */
function createFile(fileId, name, type, fileSize, sizeText, timeText, parseStatus, graphStatus, progress) {
  const parseMap = {
    PENDING: ['待解析', 'waiting'],
    PROCESSING: ['解析中', 'running'],
    SUCCESS: ['已入库', 'ready'],
    FAILED: ['解析失败', 'danger'],
  }
  const graphMap = {
    NONE: ['待解析', 'waiting'],
    BUILDING: ['构建中', 'running'],
    SUCCESS: ['已构建', 'ready'],
    FAILED: ['构建失败', 'danger'],
  }
  return {
    id: fileId,
    fileId,
    name,
    originalName: name,
    originalFileName: name,
    type,
    fileType: type,
    fileCategory: type,
    fileSize,
    size: fileSize,
    sizeText,
    timeText,
    uploadStatus: 'UPLOADED',
    status: 'UPLOADED',
    statusText: '已上传',
    statusTone: 'green',
    parseStatus,
    graphStatus,
    knowledgeText: parseMap[parseStatus]?.[0] || '待解析',
    knowledgeTone: parseMap[parseStatus]?.[1] || 'waiting',
    graphText: graphMap[graphStatus]?.[0] || '待解析',
    graphTone: graphMap[graphStatus]?.[1] || 'waiting',
    progress,
    createdAt: timeText,
    updateTime: timeText,
  }
}

/**
 * 解析 axios 请求体，兼容对象和 JSON 字符串两种形式。
 */
function parseBody(data) {
  if (!data) return {}
  if (typeof data === 'string') {
    try {
      return JSON.parse(data)
    } catch {
      return {}
    }
  }
  return data
}

/**
 * 生成统一分页结构，兼容 Element 表格和后端分页接口。
 */
function page(records, pageNum = 1, pageSize = 10) {
  return {
    records,
    list: records,
    rows: records,
    total: records.length,
    pageNum,
    pageSize,
    pages: Math.max(1, Math.ceil(records.length / pageSize)),
  }
}

function success(data, message = 'success') {
  return { code: 200, message, data }
}

function demoLoginData(username = 'demo') {
  return {
    token: demoToken,
    accessToken: demoToken,
    refreshToken: 'static-demo-refresh-token',
    sessionId: 'static-demo-session',
    userId: 1,
    username,
    role: 'ADMIN',
    user: { ...demoUser, username },
  }
}

/**
 * 判断是否启用静态演示模式；默认启用，接真实后端时可显式关闭。
 */
export function isStaticDemoEnabled() {
  return import.meta.env.VITE_STATIC_DEMO !== 'false'
    && localStorage.getItem('docnexus_static_demo') !== 'false'
}

/**
 * 注入本地演示登录态，让所有需要登录的页面都能直接打开。
 */
export function ensureStaticDemoSession() {
  if (!isStaticDemoEnabled()) return
  localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, demoToken)
  localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, 'static-demo-refresh-token')
  localStorage.setItem(STORAGE_KEYS.SESSION_ID, 'static-demo-session')
  localStorage.setItem(STORAGE_KEYS.USER_INFO, JSON.stringify(demoUser))
  localStorage.setItem('userId', '1')
  localStorage.setItem('userName', demoUser.username)
}

function blobResponse(config) {
  const blob = new Blob(['DocNexus Cloud 静态演示文件内容。'], { type: 'text/plain;charset=utf-8' })
  return { data: blob, status: 200, statusText: 'OK', headers: {}, config }
}

function normalizedUrl(config) {
  const base = String(config.baseURL || '')
  const raw = String(config.url || '')
  return raw.startsWith('/api') ? raw.replace(/^\/api/, '') : raw.replace(base, '')
}

/**
 * 按接口路径返回静态数据，覆盖页面初始化、上传、AI、学习室和个人中心等常用请求。
 */
function route(config) {
  const method = String(config.method || 'get').toLowerCase()
  const url = normalizedUrl(config)
  const body = parseBody(config.data)
  const params = config.params || {}

  if (config.responseType === 'blob' || config.responseType === 'arraybuffer') return blobResponse(config)

  if (url === '/auth/login' && method === 'post') return success(demoLoginData(body.username || 'demo'), '登录成功')
  if (url === '/auth/register' && method === 'post') return success(demoLoginData(body.username || 'demo'), '注册成功')
  if (url === '/auth/refresh' && method === 'post') return success(demoLoginData('demo'))
  if (url === '/auth/logout') return success(null, '已退出')
  if (url.startsWith('/auth/password/recovery')) return success({ verified: true }, '静态演示已通过')

  if (url === '/users/me') return success(demoUser)
  if (url.startsWith('/users/me/profile')) return success({ ...demoUser, ...body }, '资料已保存')
  if (url === '/users/me/profile-cache') return success(null)
  if (url === '/users/me/sessions') {
    return success(page([
      {
        sessionId: 'static-demo-session',
        deviceName: 'Chrome / Windows',
        ipAddress: '127.0.0.1',
        online: true,
        current: true,
        loginAtMillis: now - 1000 * 60 * 30,
        lastActiveAtMillis: now - 1000 * 60 * 2,
      },
    ], 1, 5))
  }
  if (url.includes('/sessions/heartbeat')) return success({ online: true })
  if (url.startsWith('/users/')) return success(demoUser)

  if (url === '/files/list') {
    const needsPage = params.pageNum || params.pageSize || params.knowledgeBaseId
    return success(needsPage ? page(demoFiles, Number(params.pageNum || 1), Number(params.pageSize || 10)) : demoFiles)
  }
  if (url === '/files/search') return success(page(demoFiles))
  if (url.startsWith('/files/metadata/')) return success(demoFiles[0])
  if (url === '/files/upload') {
    const id = `file_demo_${Math.floor(Math.random() * 9000 + 1000)}`
    const item = createFile(id, '新上传资料.pdf', 'PDF', 1887436, '1.8 MB', '刚刚', 'PENDING', 'NONE', 0)
    demoFiles.unshift(item)
    return success({ uploadId: `upload_${id}`, file: item }, '上传成功')
  }
  if (url === '/files/multipart/init') return success({ uploadId: 'upload_demo_chunk', fileId: 'file_demo_chunk', chunkSize: 10485760, totalChunks: 3, uploadedChunks: [] })
  if (url === '/files/multipart/chunk') return success({ uploadId: 'upload_demo_chunk', status: 'UPLOADING', uploadedChunks: 1, totalChunks: 3, uploadedChunkIndexes: [0] })
  if (url === '/files/multipart/complete') return success({ uploadId: 'upload_demo_chunk', file: demoFiles[0] }, '上传成功')
  if (url.startsWith('/files/multipart/status/')) return success({ uploadId: 'upload_demo_chunk', status: 'INTERRUPTED', uploadedChunks: 1, totalChunks: 3, uploadedChunkIndexes: [0], errorMessage: '' })
  if (url.includes('/files/uploads')) return success(null)
  if (url.startsWith('/files/')) return success(null)

  if (url.startsWith('/documents')) {
    return success([
      {
        id: 'doc_001',
        title: '招标文件解析稿',
        summary: '项目范围、评分办法、交付风险已提取。',
        version: 3,
        status: 'active',
        category: '招投标',
        tags: ['招标', '风险'],
        updateTime: '06/03 10:00',
      },
    ])
  }
  if (url === '/factory/steps') {
    return success([
      { id: 'step_1', name: '资料理解', status: 'done' },
      { id: 'step_2', name: '生成初稿', status: 'running' },
      { id: 'step_3', name: '引用审阅', status: 'pending' },
    ])
  }

  if (url === '/ai/todos' && method === 'get') return success(demoTodos)
  if (url === '/ai/todos' && method === 'post') {
    const todo = { id: `todo_${Date.now()}`, status: 'pending', createdAt: '刚刚', ...body }
    demoTodos.unshift(todo)
    return success(todo)
  }
  if (url.startsWith('/ai/todos/') && url.endsWith('/steps')) {
    return success([
      { time: '10:12', title: '读取资料', detail: '已加载 3 个来源文档', status: 'done' },
      { time: '10:13', title: '生成摘要', detail: '正在整理关键观点', status: 'running' },
    ])
  }
  if (url.includes('/run')) return success({ ...(demoTodos[0] || {}), status: 'running' })
  if (url.startsWith('/ai/todos/')) return success(body || null)

  if (url === '/agent/tasks') return success(demoAgentTasks)
  if (url.startsWith('/agent/tasks/')) return success(demoAgentTasks[0])
  if (url.startsWith('/ai/aiops/monitor')) return success({ 'ai.request.stats': { count: 128, avg: 860, p95: 1800 }, 'ai.errors': 2 })
  if (url.startsWith('/ai/aiops/health')) return success({ status: 'UP', models: ['mock-qwen', 'mock-gpt'], redis: 'UP', minio: 'UP' })
  if (url.startsWith('/ai/aiops/faults')) return success([{ id: 'fault_001', level: 'LOW', title: '静态演示告警', resolved: false }])
  if (url.startsWith('/ai/aiops')) return success({})
  if (url.startsWith('/ai/')) return success({ answer: '这是静态演示模式下的 AI 回复。', content: '静态演示结果', keywords: ['知识库', '文档', 'AI'] })

  if (url === '/study/chat/windows') return success(demoChatWindows)
  if (url === '/study/chat/start') return success({ id: `conv_${Date.now()}`, title: '新的学习对话' })
  if (url.includes('/study/chat/') && url.endsWith('/history')) return success([{ role: 'assistant', content: '欢迎进入静态学习室，可以围绕上传资料提问。' }])
  if (url === '/study/chat') return success({ role: 'assistant', content: '这是基于静态知识库的模拟回答，后端接入后会替换为真实 RAG。' })
  if (url.startsWith('/study/chat/')) return success({})

  if (url.startsWith('/knowledge-library')) return success({})
  return success({})
}

/**
 * axios 静态适配器：拦截所有后端请求并返回本地演示数据。
 */
export function createStaticDemoAdapter(config) {
  ensureStaticDemoSession()
  return new Promise((resolve) => {
    window.setTimeout(() => {
      const result = route(config)
      resolve(result.status ? result : { data: result, status: 200, statusText: 'OK', headers: {}, config })
    }, 120)
  })
}
