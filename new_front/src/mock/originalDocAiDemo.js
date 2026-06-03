import { STORAGE_KEYS } from '../constants'

const demoUser = {
  id: 1,
  username: 'DocAI',
  email: 'docai@example.com',
  phone: '13800000000',
  role: 'ADMIN',
}

let documents = [
  {
    id: 1,
    fileId: 'file_001',
    name: '项目可行性研究报告.docx',
    title: '项目可行性研究报告.docx',
    content: '<h1>项目可行性研究报告</h1><p>本文档用于演示 DocAI-main 原始前端的文档阅读、AI 摘要、文本润色和版本管理界面。</p><p>当前数据来自本地 Mock，不依赖后端服务。</p>',
    summary: '这是一份用于展示原始 DocAI 前端的示例文档，包含项目背景、实施计划和风险说明。',
    category: 'default',
    createTime: '2026-06-03 09:20:00',
    updateTime: '2026-06-03 10:10:00',
    color: 'blue',
  },
  {
    id: 2,
    fileId: 'file_002',
    name: '会议纪要与行动项.pdf',
    title: '会议纪要与行动项.pdf',
    content: '<h1>会议纪要</h1><p>本次会议讨论了产品路线、交付节奏和资料归档规范。</p>',
    summary: '会议明确了三个行动项：完善资料库、补充 AI 分析流程、准备演示文档。',
    category: 'meeting',
    createTime: '2026-06-02 15:30:00',
    updateTime: '2026-06-02 16:00:00',
    color: 'purple',
  },
  {
    id: 3,
    fileId: 'file_003',
    name: '产品需求说明书.md',
    title: '产品需求说明书.md',
    content: '<h1>产品需求说明书</h1><p>系统需要支持文件上传、智能解析、AI 对话、协作编辑和运维监控。</p>',
    summary: '需求覆盖文档管理、AI 辅助创作、RAG 问答和系统监控。',
    category: 'prd',
    createTime: '2026-06-01 18:05:00',
    updateTime: '2026-06-01 19:40:00',
    color: 'green',
  },
]

const versions = [
  { id: 101, versionNumber: 3, title: '项目可行性研究报告.docx', createTime: '2026-06-03 10:10:00' },
  { id: 102, versionNumber: 2, title: '项目可行性研究报告.docx', createTime: '2026-06-03 09:45:00' },
  { id: 103, versionNumber: 1, title: '项目可行性研究报告.docx', createTime: '2026-06-03 09:20:00' },
]

const faults = [
  { id: 'fault_001', type: 'TIMEOUT', level: 'WARN', message: 'AI 摘要接口近期平均耗时偏高', resolved: false, time: '10:16' },
  { id: 'fault_002', type: 'QUEUE', level: 'INFO', message: '异步任务队列存在短暂积压', resolved: false, time: '10:20' },
]

/**
 * 判断是否启用原始 DocAI 静态演示；默认开启。
 */
export function isOriginalDemoEnabled() {
  return import.meta.env.VITE_STATIC_DEMO !== 'false'
    && localStorage.getItem('docai_main_static_demo') !== 'false'
}

/**
 * 写入原项目前端需要的本地登录态。
 */
export function ensureOriginalDemoSession() {
  if (!isOriginalDemoEnabled()) return
  localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, 'docai-main-static-token')
  localStorage.setItem('accessToken', 'docai-main-static-token')
  localStorage.setItem('userId', String(demoUser.id))
  localStorage.setItem('userName', demoUser.username)
  localStorage.setItem('username', demoUser.username)
}

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

function success(data, message = 'success') {
  return { code: 200, message, data }
}

function normalizedUrl(config) {
  const raw = String(config.url || '')
  return raw.startsWith('/api') ? raw.replace(/^\/api/, '') : raw
}

function blobResponse(config) {
  const blob = new Blob(['DocAI-main 静态演示下载内容'], { type: 'text/plain;charset=utf-8' })
  return { data: blob, status: 200, statusText: 'OK', headers: {}, config }
}

function getDocumentById(id) {
  return documents.find(item => String(item.id) === String(id)) || documents[0]
}

/**
 * 根据原始 DocAI-main 后端路径返回页面所需数据。
 */
function route(config) {
  const method = String(config.method || 'get').toLowerCase()
  const url = normalizedUrl(config)
  const body = parseBody(config.data)
  const params = config.params || {}

  if (config.responseType === 'blob' || config.responseType === 'arraybuffer') return blobResponse(config)

  if (url === '/users/login' && method === 'post') {
    ensureOriginalDemoSession()
    return success({
      token: 'docai-main-static-token',
      accessToken: 'docai-main-static-token',
      userId: demoUser.id,
      username: body.username || demoUser.username,
      role: demoUser.role,
    }, '登录成功')
  }
  if (url === '/users/register' && method === 'post') return success({ ...demoUser, username: body.username || demoUser.username }, '注册成功')
  if (url.startsWith('/users/')) return success(demoUser)

  if (url.startsWith('/documents/user/')) return success(documents)
  if (url === '/documents/search') {
    const keyword = String(params.keyword || '').trim()
    const result = keyword
      ? documents.filter(doc => doc.name.includes(keyword) || doc.summary.includes(keyword) || doc.content.includes(keyword))
      : documents
    return success(result)
  }
  if (url === '/documents' && method === 'post') {
    const id = Date.now()
    const doc = {
      id,
      fileId: body.fileId || `file_${id}`,
      name: body.title || body.name || '新建文档.docx',
      title: body.title || body.name || '新建文档.docx',
      content: body.content || '<p>这是静态演示中新建的文档。</p>',
      summary: body.summary || '静态演示文档摘要。',
      category: body.category || 'default',
      createTime: '刚刚',
      updateTime: '刚刚',
      color: 'blue',
    }
    documents.unshift(doc)
    return success(doc)
  }
  if (/^\/documents\/[^/]+\/versions$/.test(url)) return success(versions)
  if (/^\/documents\/[^/]+\/restore\/[^/]+$/.test(url)) return success(null, '已恢复版本')
  if (/^\/documents\/[^/]+$/.test(url) && method === 'get') {
    const id = url.split('/').pop()
    return success(getDocumentById(id))
  }
  if (/^\/documents\/[^/]+$/.test(url) && method === 'put') {
    const id = url.split('/').pop()
    documents = documents.map(item => String(item.id) === String(id) ? { ...item, ...body, name: body.title || item.name, updateTime: '刚刚' } : item)
    return success(getDocumentById(id), '保存成功')
  }
  if (/^\/documents\/[^/]+$/.test(url) && method === 'delete') {
    const id = url.split('/').pop()
    documents = documents.filter(item => String(item.id) !== String(id))
    return success(null, '删除成功')
  }

  if (url === '/files/list') return success(documents)
  if (url === '/files/search') return success(documents)
  if (url === '/files/upload' && method === 'post') {
    const id = Date.now()
    return success({ fileId: `file_${id}`, originalFileName: '静态上传文件.docx', fileName: '静态上传文件.docx' }, '上传成功')
  }
  if (url.startsWith('/files/metadata/')) return success({ fileId: 'file_001', originalFileName: documents[0].name })
  if (url.startsWith('/files/')) return success(null)

  if (url === '/ai/aiops/monitor') return success({ 'ai.request.stats': { count: 128, avg: 860, p95: 1800 }, 'ai.errors': 2 })
  if (url === '/ai/aiops/health') return success({ status: 'UP', aiService: 'UP', fileService: 'UP', documentService: 'UP' })
  if (url === '/ai/aiops/detect') return success(faults)
  if (url === '/ai/aiops/faults') return success(faults)
  if (url.includes('/ai/aiops/faults/') && url.endsWith('/resolve')) return success(null, '已处理')
  if (url.startsWith('/ai/aiops/metrics')) return success(null)
  if (url.startsWith('/ai/async/jobs/')) return success({ status: 'SUCCESS', result: '这是静态演示 AI 任务结果。' })
  if (url === '/ai/agent/tools') return success([{ name: '文档摘要' }, { name: '知识库问答' }, { name: '文本润色' }])
  if (url.includes('/ai/agent/chat/start')) return success({ conversationId: 'chat_static_001' })
  if (url.startsWith('/ai/')) return success({
    result: '这是 DocAI-main 静态演示模式下的 AI 回复。',
    answer: '这是 DocAI-main 静态演示模式下的 AI 回复。',
    summary: '静态摘要：文档重点已经提炼完成。',
    keywords: ['DocAI', '文档', 'AI'],
  })

  return success({})
}

/**
 * axios adapter：不改原页面，只拦截请求并返回本地数据。
 */
export function createOriginalDemoAdapter(config) {
  ensureOriginalDemoSession()
  return new Promise((resolve) => {
    window.setTimeout(() => {
      const result = route(config)
      resolve(result.status ? result : { data: result, status: 200, statusText: 'OK', headers: {}, config })
    }, 100)
  })
}
