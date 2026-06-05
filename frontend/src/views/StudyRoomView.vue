<template>
  <StudioLayout>
    <div class="study-page">
      <header class="study-header">
        <div>
          <span class="eyebrow">AI Study Room</span>
          <h1>AI 学习室</h1>
          <p>围绕已上传资料进行问答、追问、总结和引用溯源，把零散文献沉淀为可复用的项目记忆。</p>
        </div>
      </header>

      <section class="study-shell">
        <aside class="study-sidebar">
          <section class="sidebar-section">
            <div class="sidebar-title">
              <h2>学习会话</h2>
              <div class="sidebar-actions">
                <button type="button" class="icon-button primary-icon" title="新建对话" @click="startConversation">
                  <Plus />
                </button>
                <button type="button" class="icon-button" title="刷新会话" @click="loadConversations">
                  <Refresh />
                </button>
              </div>
            </div>
            <div
              v-for="conversation in conversations"
              :key="conversation.id"
              :class="['conversation-item', { active: conversation.id === conversationId }]"
            >
              <div v-if="editingConversationId === conversation.id" class="conversation-edit" @click.stop>
                <input
                  v-model="editingTitle"
                  maxlength="80"
                  autofocus
                  placeholder="输入会话名称"
                  @keyup.enter="saveConversationTitle(conversation)"
                  @keyup.esc="cancelRename"
                />
                <button type="button" class="mini-action success" title="保存名称" @click="saveConversationTitle(conversation)">
                  <Check />
                </button>
                <button type="button" class="mini-action" title="取消编辑" @click="cancelRename">
                  <Close />
                </button>
              </div>

              <button v-else type="button" class="conversation-main" @click="selectConversation(conversation.id)">
                <strong>{{ conversation.title || '新的资料对话' }}</strong>
                <span>{{ conversation.messageCount || 0 }} 条消息</span>
                <small>{{ conversation.lastMessage || '等待开始提问' }}</small>
              </button>

              <div v-if="editingConversationId !== conversation.id" class="conversation-actions">
                <button type="button" class="mini-action" title="编辑名称" @click.stop="beginRename(conversation)">
                  <EditPen />
                </button>
                <button type="button" class="mini-action danger" title="删除会话" @click.stop="deleteConversation(conversation)">
                  <Delete />
                </button>
              </div>
            </div>
            <div v-if="conversations.length === 0" class="sidebar-empty">还没有对话窗口。</div>
          </section>

          <section class="sidebar-section material-section">
            <div class="sidebar-title">
              <h2>已有资料</h2>
              <button type="button" class="icon-button" title="刷新资料" @click="loadFiles">
                <Refresh />
              </button>
            </div>
            <div v-for="file in files" :key="file.fileId" class="material-item">
              <span class="material-icon"><Document /></span>
              <span>
                <strong :title="file.fileName">{{ displayFileName(file.fileName) }}</strong>
                <small>{{ statusText(file) }} · {{ displayFileType(file) }}</small>
              </span>
            </div>
            <div v-if="files.length === 0" class="sidebar-empty">暂无资料，请先上传并等待索引完成。</div>
          </section>
        </aside>

        <main class="study-chat-panel">
          <div class="chat-toolbar">
            <div>
              <h2>基于资料的学习对话</h2>
              <p>DeepSeek 会结合 RAG 片段、会话历史和压缩记忆生成回答。</p>
            </div>
            <div class="toolbar-actions">
              <span class="model-badge">DeepSeek + RAG</span>
              <button type="button" class="secondary-button compact-button" :disabled="!conversationId" @click="clearConversation">
                <Delete />
                清空
              </button>
            </div>
          </div>

          <div ref="threadRef" :class="['chat-thread', { welcome: messages.length === 0 && !loading }]">
            <div v-if="messages.length === 0 && !loading" class="message-row assistant welcome-message">
              <div class="message-bubble">
                <p>你好，我是你的 AI 文档学习助手。你可以直接围绕已上传资料提问、追问、总结、提炼观点，我会结合知识库和对话历史生成可追溯回答。</p>
              </div>
            </div>

            <article v-for="message in messages" :key="message.id" :class="['message-row', message.role]">
              <div class="message-bubble">
                <div class="rendered-message" v-html="formatMessage(message.content)"></div>
                <div v-if="message.sources?.length" class="source-list">
                  <span v-for="source in message.sources" :key="sourceKey(source)">
                    {{ displayFileName(source.fileName || '未知资料') }}
                  </span>
                </div>
              </div>
            </article>

            <article v-if="loading" class="message-row assistant">
              <div class="message-bubble loading-bubble" aria-live="polite">
                <span class="thinking-text">模型正在思考中</span>
                <span class="thinking-dots" aria-hidden="true">
                  <span />
                  <span />
                  <span />
                </span>
              </div>
            </article>
          </div>

          <div class="quick-prompts">
            <button type="button" @click="fillPrompt('请总结我选中的资料，并列出关键引用来源')">总结资料</button>
            <button type="button" @click="fillPrompt('请提炼这批文献的核心创新点和不足')">提炼创新点</button>
            <button type="button" @click="fillPrompt('请基于资料整理一段论文式回答')">论文式回答</button>
          </div>

          <form class="ask-box" @submit.prevent="sendMessage">
            <input
              v-model="input"
              :disabled="loading"
              placeholder="继续追问资料内容，或让 AI 整理成论文段落"
            />
            <button title="发送" type="submit" :disabled="loading || !input.trim()">
              <Promotion />
            </button>
          </form>
        </main>
      </section>
    </div>
  </StudioLayout>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import StudioLayout from '../components/StudioLayout.vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Close, Delete, Document, EditPen, Plus, Promotion, Refresh } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import katex from 'katex'
import DOMPurify from 'dompurify'
import 'katex/dist/katex.min.css'
import request from '../utils/request'
import { filterDeletingFiles, subscribeFileDeleting } from '../utils/deletedFiles'
import { removeFileExtension, resolveFileTypeLabel } from '../utils/fileDisplay'

const conversationId = ref('')
const conversations = ref([])
const files = ref([])
const messages = ref([])
const input = ref('')
const loading = ref(false)
const threadRef = ref(null)
const editingConversationId = ref('')
const editingTitle = ref('')
let unsubscribeFileDeleting = null

const markdownRenderer = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  breaks: true,
})

function statusText(file) {
  if (file.understandStatus === 'INDEXED') return '已索引'
  if (file.understandStatus === 'INDEXING') return `理解中 ${file.understandProgress || 0}%`
  if (file.understandStatus === 'FAILED') return '理解失败'
  return '待理解'
}

function sourceKey(source) {
  return source.fileName || source.fileId || Math.random()
}

function displayFileName(fileName) {
  return removeFileExtension(fileName)
}

function displayFileType(file) {
  return file.fileType || resolveFileTypeLabel(file.fileName || file.filename, 'unknown')
}

function normalizeSources(sources) {
  if (!Array.isArray(sources)) return []
  const seen = new Set()
  const result = []
  for (const source of sources) {
    const fileName = source?.fileName || source?.metadata?.fileName
    if (!fileName || seen.has(fileName)) continue
    seen.add(fileName)
    result.push({ fileName })
  }
  return result
}

function formatMessage(content) {
  const raw = normalizeLatexDelimiters(String(content || '').trim())
  if (!raw) return ''

  /*
   * ChatGPT 类产品的回答通常是 Markdown + LaTeX 混合文本。
   * 这里不再依赖 markdown-it-katex，因为该插件内置的 KaTeX 版本较旧，复杂上下标容易错位。
   * 现在先把公式替换成占位符，使用项目中的 KaTeX 0.16 直接渲染，再渲染 Markdown，最后还原公式 HTML。
   * 最后交给 DOMPurify 清洗，避免 v-html 直接渲染模型输出带来的脚本注入风险。
   */
  const rendered = renderMarkdownWithMath(raw)
  return DOMPurify.sanitize(rendered, {
    ADD_ATTR: ['target', 'rel', 'class', 'style', 'aria-hidden'],
  })
}

function normalizeLatexDelimiters(content) {
  /*
   * KaTeX 插件天然支持 $...$ 和 $$...$$。很多模型会输出 \(...\) / \[...\]，
   * 这里在渲染前统一转换，保证学习室能稳定显示行内公式和块级公式。
   *
   * 另外，模型有时会把公式误写成 `( f_m = f_c + ... )` 或反引号代码
   * ``f_m = f_c + ...``。这类内容本质是数学表达式，不应该被 Markdown 当成普通括号或 code。
   * 因此这里做一层温和的启发式修正：只转换“含等号/上下标/数学运算符/希腊字母”的短表达式，
   * 并且保护三反引号代码块，避免把真正代码误判成公式。
   */
  const codeBlocks = []
  const placeholderPrefix = '\u0000DOCNEXUS_CODE_BLOCK_'
  const protectedContent = content.replace(/```[\s\S]*?```/g, (block) => {
    const index = codeBlocks.push(block) - 1
    return `${placeholderPrefix}${index}\u0000`
  })

  const normalized = normalizeStandaloneMathLines(normalizeSharedDisplayMathFences(normalizeDollarMathFences(protectedContent)))
    .replace(/\\\[([\s\S]+?)\\\]/g, (_, expression) => `\n$$\n${expression.trim()}\n$$\n`)
    .replace(/\\\(([\s\S]+?)\\\)/g, (_, expression) => `$${expression.trim()}$`)
    .replace(/`([^`\n]+?)`/g, (match, expression) => {
      const normalizedExpression = normalizeMathExpression(expression)
      return isLikelyMathExpression(normalizedExpression) ? wrapMathExpression(normalizedExpression) : match
    })
    .replace(/(^|[，。；：、])\(\s*([^()\n]{2,220}?)\s*\)/g, (match, prefix, expression) => {
      const normalizedExpression = normalizeMathExpression(expression)
      return isLikelyMathExpression(normalizedExpression) ? `${prefix}${wrapMathExpression(normalizedExpression)}` : match
    })

  return codeBlocks.reduce(
    (result, block, index) => result.replace(`${placeholderPrefix}${index}\u0000`, block),
    normalized,
  )
}

function normalizeDollarMathFences(content) {
  /*
   * 模型偶尔会把块级公式边界写成 $$$$、$$$$$ 这类非标准写法。
   * KaTeX 和前端解析器只应接收标准 $$，因此先把“独占一行的 3 个及以上美元符号”规整为 $$。
   * 另外，DeepSeek 等模型在 Markdown 场景下有时会把块级公式边界转义成 \$$\$$ 的可见形态：
   * 原始文本实际是 \\\$\\\$，Markdown 渲染后用户看到仍然是 $$，但公式解析器不会把它当成定界符。
   * 所以这里按“整行是否只包含被转义或未转义的美元符号”判断，统一还原为标准 $$。
   * 只处理独占一行的 fence，不碰正文中的金额、变量或普通 $...$ 行内公式。
   */
  return String(content || '')
    .split('\n')
    .map((line) => (isDollarFenceLine(line) ? '$$' : line))
    .join('\n')
}

function isDollarFenceLine(line) {
  const compact = String(line || '').trim().replace(/\s+/g, '')
  if (!compact) return false
  const unescaped = compact.replace(/\\\$/g, '$')
  return /^\${2,}$/.test(unescaped)
}

function normalizeSharedDisplayMathFences(content) {
  /*
   * 模型有时会把 $$ 当作多个公式之间的“分隔线”：
   * $$
   * formula A
   * $$
   * formula B
   * $$
   * formula C
   * $$
   *
   * 标准解析会把中间的 $$ 只当作前一个公式的结束符，导致 formula B 裸露。
   * 如果某一行 $$ 的前后非空行都像数学表达式，就把这一行复制成“结束上一段 + 开始下一段”。
   */
  const lines = String(content || '').split('\n')
  const result = []
  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index]
    result.push(line)
    if (line.trim() !== '$$') continue
    const previous = nearestNonEmptyLine(lines, index, -1)
    const next = nearestNonEmptyLine(lines, index, 1)
    if (previous && next && isLikelyStandaloneMathLine(previous) && isLikelyStandaloneMathLine(next)) {
      result.push('$$')
    }
  }
  return result.join('\n')
}

function nearestNonEmptyLine(lines, startIndex, direction) {
  for (let index = startIndex + direction; index >= 0 && index < lines.length; index += direction) {
    const value = lines[index].trim()
    if (value) return value
  }
  return ''
}

function renderMarkdownWithMath(content) {
  const mathBlocks = []
  const codeBlocks = []
  const mathPlaceholderPrefix = 'DOCNEXUSMATHPLACEHOLDER'
  const codePlaceholderPrefix = '\u0000DOCNEXUS_RENDER_CODE_BLOCK_'

  /*
   * 渲染顺序必须是“先公式、后 Markdown”。
   * 如果先让 Markdown 处理多行 $$...$$，它会把定界符渲染成普通段落和 <br>，
   * 用户就会看到裸露的 $$。这里先保护代码块，再用按行状态机提取块级公式，
   * 最后才处理行内公式，能覆盖论文回答里最常见的多行矩阵、求和、argmax 等公式。
   */
  const protectedContent = String(content || '').replace(/```[\s\S]*?```/g, (block) => {
    const index = codeBlocks.push(block) - 1
    return `${codePlaceholderPrefix}${index}\u0000`
  })

  const createMathPlaceholder = (expression, displayMode) => {
    const index = mathBlocks.length
    mathBlocks.push(renderKatex(expression, displayMode))
    return `${mathPlaceholderPrefix}${index}END`
  }

  const withDisplayPlaceholders = extractDisplayMathBlocks(protectedContent, createMathPlaceholder)
  const withPlaceholders = extractMathExpressions(withDisplayPlaceholders, createMathPlaceholder)
  let html = markdownRenderer.render(withPlaceholders)
  mathBlocks.forEach((block, index) => {
    html = html.replaceAll(`${mathPlaceholderPrefix}${index}END`, block)
  })
  codeBlocks.forEach((block, index) => {
    html = html.replaceAll(`${codePlaceholderPrefix}${index}\u0000`, block)
  })
  return html
}

function extractDisplayMathBlocks(content, replacer) {
  /*
   * 块级公式采用行级状态机解析，而不是单纯查找下一个 $$。
   * 这样可以稳定处理：
   * $$
   * 多行公式
   * $$
   * 也能避免 Markdown 在公式提取前把 $$ 渲染成普通文本。
   */
  const lines = String(content || '').split('\n')
  const result = []
  let collecting = false
  let expressionLines = []

  for (const line of lines) {
    if (line.trim() === '$$') {
      if (collecting) {
        const expression = expressionLines.join('\n').trim()
        result.push(expression ? replacer(expression, true) : '$$')
        expressionLines = []
        collecting = false
      } else {
        collecting = true
        expressionLines = []
      }
      continue
    }

    if (collecting) {
      expressionLines.push(line)
    } else {
      result.push(line)
    }
  }

  if (collecting) {
    result.push('$$', ...expressionLines)
  }

  return result.join('\n')
}

function extractMathExpressions(content, replacer) {
  let result = ''
  let index = 0
  while (index < content.length) {
    if (content.startsWith('$$', index)) {
      const end = content.indexOf('$$', index + 2)
      if (end !== -1) {
        const expression = content.slice(index + 2, end).trim()
        result += expression ? replacer(expression, true) : '$$'
        index = end + 2
        continue
      }
    }
    if (content[index] === '$' && content[index + 1] !== '$') {
      const end = findInlineMathEnd(content, index + 1)
      if (end !== -1) {
        const expression = content.slice(index + 1, end).trim()
        result += expression ? replacer(expression, false) : '$$'
        index = end + 1
        continue
      }
    }
    result += content[index]
    index += 1
  }
  return result
}

function findInlineMathEnd(content, start) {
  for (let index = start; index < content.length; index += 1) {
    if (content[index] === '\\') {
      index += 1
      continue
    }
    if (content[index] === '$') {
      return index
    }
  }
  return -1
}

function renderKatex(expression, displayMode) {
  const normalized = normalizeKatexExpression(expression)
  try {
    return katex.renderToString(normalized, {
      displayMode,
      throwOnError: true,
      strict: 'ignore',
      trust: false,
      output: 'html',
    })
  } catch (error) {
    return displayMode
      ? `<pre class="math-fallback">${escapeHtml(normalized)}</pre>`
      : `<code class="math-fallback">${escapeHtml(normalized)}</code>`
  }
}

function normalizeStandaloneMathLines(content) {
  return content
    .split('\n')
    .map((line) => {
      const trimmed = line.trim()
      if (!trimmed || trimmed.startsWith('$$') || trimmed.includes('\u0000DOCNEXUS_CODE_BLOCK_')) return line
      if (!isLikelyStandaloneMathLine(trimmed)) return line
      const expression = trimmed
        .replace(/^\\\[/, '')
        .replace(/\\\]$/, '')
        .replace(/^\\\(/, '')
        .replace(/\\\)$/, '')
        .replace(/\$(.*?)\$/g, '$1')
        .trim()
      return `\n$$\n${expression}\n$$`
    })
    .join('\n')
}

function normalizeMathExpression(expression) {
  return String(expression || '')
    .replace(/\s+/g, ' ')
    .trim()
}

function normalizeKatexExpression(expression) {
  return String(expression || '')
    /*
     * 资料解析和模型转述论文公式时，最容易出现“命令和变量粘连”的问题。
     * 例如 OCR/LLM 可能把 e^{-j\pi (i-1)\beta_m ...} 写成 e^{-j\pii-1\beta_m ...}，
     * KaTeX 会把 \pii 当作未知命令并渲染成红色错误。这里只修正常见的 \pi + 变量 - 1 场景，
     * 不做大范围猜测，避免把用户真正想表达的公式改坏。
     */
    .replace(/\\pi\s*([A-Za-z])\s*-\s*1/g, '\\pi ($1-1)')
    /*
     * DeepSeek 在从资料片段组织回答时，有时会漏掉下标符号：
     * \mathbf{A}{\text{conv}} 应为 \mathbf{A}_{\text{conv}}，
     * \theta{N_s} 应为 \theta_{N_s}。这类写法虽然能被部分 Markdown 展示，
     * 但数学含义和排版都会明显偏离论文原式，所以在展示层做轻量规整。
     */
    .replace(/(\\mathbf\{[A-Za-z]\})\s*\{\\text\{([^{}]+)\}\}/g, '$1_{\\text{$2}}')
    .replace(/\\(theta|alpha|beta|gamma|tau)\s*\{([^{}]+)\}/g, '\\$1_{$2}')
    .replace(/\{text\{([^{}]+)\}\}/g, '{\\text{$1}}')
    .replace(/\\dots\b/g, '\\ldots')
    .replace(/\\mathrm\s*\{([^{}]+)\}/g, '\\operatorname{$1}')
    // 模型有时会在块级公式末尾残留一个或多个换行命令 \\，末尾孤立存在时 KaTeX 会解析失败。
    .replace(/\\+\s*$/g, '')
    .replace(/\s+/g, ' ')
    .trim()
}

function isLikelyStandaloneMathLine(line) {
  const value = normalizeMathExpression(line)
  if (!value || value.length > 500) return false
  if (/[\u4e00-\u9fff]/.test(value)) return false
  const hasLatexCommand = /\\[A-Za-z]+/.test(value)
  const hasMathSignal = /[=<>≈≤≥]|[_^{}]|[+\-*/]|[α-ωΑ-Ω]/.test(value)
  return hasLatexCommand && hasMathSignal
}

function isLikelyMathExpression(expression) {
  const value = normalizeMathExpression(expression)
  if (!value || value.length > 220) return false
  if (/[\u4e00-\u9fff]/.test(value)) return false
  if (/^https?:\/\//i.test(value)) return false

  const hasIdentifier = /[A-Za-zα-ωΑ-Ω]/.test(value)
  const hasMathSignal = /[=<>≈≤≥]|\\[A-Za-z]+|[_^{}]|[+\-*/]|[α-ωΑ-Ω]/.test(value)
  return hasIdentifier && hasMathSignal
}

function wrapMathExpression(expression) {
  const value = normalizeMathExpression(expression)
  const operatorCount = (value.match(/[=<>≈≤≥+\-*/]/g) || []).length
  const shouldUseDisplayMath = value.length > 42 || operatorCount >= 3 || value.includes('\\\\')
  return shouldUseDisplayMath ? `\n$$\n${value}\n$$\n` : `$${value}$`
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

async function loadConversations() {
  const response = await request.get('/study/chat/windows')
  conversations.value = response.data || []
  if (!conversationId.value && conversations.value.length > 0) {
    await selectConversation(conversations.value[0].id)
  }
}

async function loadFiles() {
  const response = await request.get('/files/list', { params: { page: 1, size: 30 } })
  files.value = filterDeletingFiles(response.data)
}

async function startConversation() {
  const response = await request.post('/study/chat/start')
  const conversation = response.data
  conversationId.value = conversation.id
  messages.value = []
  await loadConversations()
}

async function selectConversation(id) {
  conversationId.value = id
  const response = await request.get(`/study/chat/${id}/history`)
  messages.value = (response.data || []).map(normalizeMessage)
  await scrollToBottom()
}

async function clearConversation() {
  if (!conversationId.value) return
  await request.post(`/study/chat/${conversationId.value}/clear`)
  messages.value = []
  await loadConversations()
}

function beginRename(conversation) {
  editingConversationId.value = conversation.id
  editingTitle.value = conversation.title || '新的资料对话'
}

function cancelRename() {
  editingConversationId.value = ''
  editingTitle.value = ''
}

async function saveConversationTitle(conversation) {
  const title = editingTitle.value.trim()
  if (!title) {
    ElMessage.warning('会话名称不能为空')
    return
  }
  const response = await request.post(`/study/chat/${conversation.id}/rename`, { title })
  const renamed = response.data || { id: conversation.id, title }
  conversations.value = conversations.value.map((item) => (
    item.id === conversation.id ? { ...item, ...renamed, title: renamed.title || title } : item
  ))
  cancelRename()
  ElMessage.success('会话名称已更新')
}

async function deleteConversation(conversation) {
  try {
    await ElMessageBox.confirm(
      `确定要删除「${conversation.title || '新的资料对话'}」吗？该窗口的历史消息会从数据库和缓存中移除。`,
      '删除会话',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
      },
    )
  } catch (error) {
    return
  }

  await request.post(`/study/chat/${conversation.id}/end`)
  conversations.value = conversations.value.filter((item) => item.id !== conversation.id)
  if (conversationId.value === conversation.id) {
    conversationId.value = ''
    messages.value = []
    if (conversations.value.length > 0) {
      await selectConversation(conversations.value[0].id)
    }
  }
  if (editingConversationId.value === conversation.id) {
    cancelRename()
  }
  ElMessage.success('会话已删除')
}

function fillPrompt(prompt) {
  input.value = prompt
}

async function sendMessage() {
  const content = input.value.trim()
  if (!content || loading.value) return
  if (!conversationId.value) {
    await startConversation()
  }

  messages.value.push({
    id: crypto.randomUUID(),
    role: 'user',
    content,
    sources: [],
  })
  input.value = ''
  loading.value = true
  await scrollToBottom()

  try {
    const response = await request.post('/study/chat', {
      conversationId: conversationId.value,
      message: content,
      // 学习室默认覆盖多份资料，避免“总结这批文献”只召回少量片段。
      topK: 8,
    })
    const data = response.data || {}
    conversationId.value = data.conversationId || data.sessionId || conversationId.value
    messages.value.push({
      id: crypto.randomUUID(),
      role: 'assistant',
      content: data.answer || '服务器繁忙，请稍后再试',
      sources: data.answer === '服务器繁忙，请稍后再试' ? [] : normalizeSources(data.sources),
    })
    await loadConversations()
  } catch (error) {
    messages.value.push({
      id: crypto.randomUUID(),
      role: 'assistant',
      content: '服务器繁忙，请稍后再试',
      sources: [],
    })
  } finally {
    loading.value = false
    await scrollToBottom()
  }
}

function normalizeMessage(message) {
  return {
    id: message.id || crypto.randomUUID(),
    role: message.role === 'assistant' ? 'assistant' : 'user',
    content: message.content || '',
    sources: normalizeSources(message.sources),
  }
}

async function scrollToBottom() {
  await nextTick()
  if (threadRef.value) {
    threadRef.value.scrollTop = threadRef.value.scrollHeight
  }
}

onMounted(async () => {
  unsubscribeFileDeleting = subscribeFileDeleting((fileId) => {
    files.value = files.value.filter((file) => String(file.fileId || file.id || '') !== fileId)
  })
  await Promise.all([loadFiles(), loadConversations()])
})

onUnmounted(() => {
  unsubscribeFileDeleting?.()
})
</script>

<style scoped>
.study-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.study-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
}

.study-header h1 {
  margin: 6px 0;
  color: #172033;
}

.study-header p {
  margin: 0;
  color: #66748a;
}

.study-shell {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 20px;
  min-height: 760px;
}

.study-sidebar,
.study-chat-panel {
  background: #ffffff;
  border: 1px solid #dde6f2;
  border-radius: 8px;
  box-shadow: 0 16px 40px rgba(36, 57, 90, 0.08);
}

.study-sidebar {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 18px;
  min-width: 0;
}

.sidebar-section {
  min-width: 0;
}

.material-section {
  border-top: 1px solid #edf2f7;
  padding-top: 16px;
}

.sidebar-title,
.chat-toolbar,
.toolbar-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.sidebar-title h2,
.chat-toolbar h2 {
  margin: 0;
  color: #172033;
  font-size: 18px;
}

.chat-toolbar p {
  margin: 6px 0 0;
  color: #718096;
}

.sidebar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.icon-button {
  width: 32px;
  height: 32px;
  border: 1px solid #d8e2ef;
  border-radius: 8px;
  background: #fff;
  color: #526276;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.18s ease;
}

.icon-button:hover {
  border-color: #0ea5e9;
  color: #0b79b7;
  background: #f0f9ff;
}

.primary-icon {
  border-color: #0f8b5f;
  background: #0f8b5f;
  color: #ffffff;
}

.primary-icon:hover {
  border-color: #0b7651;
  background: #0b7651;
  color: #ffffff;
}

.conversation-item,
.material-item {
  width: 100%;
  border: 1px solid #e2eaf4;
  background: #f8fbff;
  border-radius: 8px;
  padding: 12px;
  margin-top: 10px;
  text-align: left;
}

.conversation-item {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: flex-start;
  transition: border-color 0.18s ease, background 0.18s ease, box-shadow 0.18s ease;
}

.conversation-item:hover {
  border-color: #b9d8ec;
  background: #ffffff;
  box-shadow: 0 10px 26px rgba(42, 70, 104, 0.08);
}

.conversation-item.active {
  border-color: #0f8b5f;
  background: linear-gradient(180deg, #f1fff9 0%, #f8fbff 100%);
  box-shadow: inset 3px 0 0 #0f8b5f;
}

.conversation-main {
  min-width: 0;
  border: 0;
  padding: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
}

.conversation-actions {
  display: flex;
  gap: 5px;
  opacity: 0.62;
  transition: opacity 0.18s ease;
}

.conversation-item:hover .conversation-actions,
.conversation-item.active .conversation-actions {
  opacity: 1;
}

.conversation-edit {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 30px 30px;
  gap: 6px;
  align-items: center;
}

.conversation-edit input {
  min-width: 0;
  height: 34px;
  border: 1px solid #b8d4e9;
  border-radius: 8px;
  padding: 0 10px;
  color: #172033;
  outline: none;
}

.conversation-edit input:focus {
  border-color: #0f8b5f;
  box-shadow: 0 0 0 3px rgba(15, 139, 95, 0.12);
}

.mini-action {
  width: 28px;
  height: 28px;
  border: 1px solid #d8e2ef;
  border-radius: 8px;
  background: #ffffff;
  color: #526276;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 0.18s ease;
}

.mini-action:hover {
  border-color: #0ea5e9;
  color: #0b79b7;
  background: #f0f9ff;
}

.mini-action.success:hover {
  border-color: #0f8b5f;
  color: #0f8b5f;
  background: #edfff7;
}

.mini-action.danger:hover {
  border-color: #ef4444;
  color: #dc2626;
  background: #fff1f2;
}

.conversation-item strong,
.material-item strong {
  display: block;
  color: #172033;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-item span,
.conversation-item small,
.material-item small {
  display: block;
  margin-top: 5px;
  color: #718096;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.material-item {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
}

.material-icon {
  color: #0f8b5f;
}

.sidebar-empty,
.empty-state {
  color: #718096;
  background: #f8fbff;
  border: 1px dashed #d8e2ef;
  border-radius: 8px;
  padding: 14px;
  margin-top: 10px;
}

.study-chat-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto auto;
  padding: 28px;
  min-width: 0;
}

.model-badge {
  border: 1px solid #d7e2f0;
  border-radius: 999px;
  color: #526276;
  padding: 8px 14px;
  white-space: nowrap;
}

.compact-button {
  padding: 8px 12px;
}

.chat-thread {
  min-height: 560px;
  max-height: 680px;
  overflow-y: auto;
  background: #f8fbff;
  border-radius: 8px;
  padding: 24px;
  margin: 20px 0;
}

.welcome-message {
  margin-top: 8px;
}

.message-row {
  display: flex;
  margin-bottom: 14px;
}

.message-row.user {
  justify-content: flex-end;
}

.message-row.assistant {
  justify-content: flex-start;
}

.message-bubble {
  max-width: min(760px, 82%);
  border-radius: 8px;
  padding: 14px 16px;
  line-height: 1.7;
  color: #243044;
  background: #ffffff;
  border: 1px solid #e1e8f2;
}

.message-row.user .message-bubble {
  background: #05865f;
  border-color: #05865f;
  color: #ffffff;
}

.message-bubble p {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.rendered-message {
  font-size: 15px;
  line-height: 1.78;
  overflow-wrap: break-word;
}

.rendered-message :deep(*) {
  max-width: 100%;
}

.rendered-message :deep(p) {
  margin: 0 0 12px;
  word-break: normal;
}

.rendered-message :deep(p:last-child),
.rendered-message :deep(ul:last-child),
.rendered-message :deep(ol:last-child),
.rendered-message :deep(pre:last-child),
.rendered-message :deep(blockquote:last-child),
.rendered-message :deep(table:last-child) {
  margin-bottom: 0;
}

.rendered-message :deep(h1),
.rendered-message :deep(h2),
.rendered-message :deep(h3) {
  color: #172033;
  font-weight: 700;
  line-height: 1.35;
  margin: 18px 0 10px;
}

.rendered-message :deep(h1:first-child),
.rendered-message :deep(h2:first-child),
.rendered-message :deep(h3:first-child) {
  margin-top: 0;
}

.rendered-message :deep(h1) {
  font-size: 24px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e6edf5;
}

.rendered-message :deep(h2) {
  font-size: 20px;
}

.rendered-message :deep(h3) {
  font-size: 17px;
}

.rendered-message :deep(ul),
.rendered-message :deep(ol) {
  margin: 8px 0 14px;
  padding-left: 22px;
}

.rendered-message :deep(li) {
  margin: 5px 0;
  word-break: normal;
}

.rendered-message :deep(blockquote) {
  margin: 12px 0;
  padding: 10px 14px;
  border-left: 3px solid #0f8b5f;
  background: #f4fbf8;
  color: #405166;
  border-radius: 0 8px 8px 0;
}

.rendered-message :deep(code) {
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 0.92em;
  border-radius: 6px;
  background: #eef4f8;
  color: #0f5f46;
  padding: 2px 5px;
}

.rendered-message :deep(pre) {
  margin: 12px 0;
  padding: 14px;
  border-radius: 8px;
  background: #101827;
  color: #e6edf7;
  overflow-x: auto;
}

.rendered-message :deep(pre code) {
  display: block;
  padding: 0;
  background: transparent;
  color: inherit;
  white-space: pre;
}

.rendered-message :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 14px 0;
  display: block;
  overflow-x: auto;
}

.rendered-message :deep(th),
.rendered-message :deep(td) {
  border: 1px solid #d8e2ef;
  padding: 8px 10px;
  text-align: left;
  vertical-align: top;
}

.rendered-message :deep(th) {
  background: #f2f7fb;
  color: #172033;
  font-weight: 700;
}

.rendered-message :deep(a) {
  color: #0879b8;
  text-decoration: none;
}

.rendered-message :deep(a:hover) {
  text-decoration: underline;
}

.rendered-message :deep(.katex-display) {
  margin: 16px 0;
  padding: 14px 14px 16px;
  overflow-x: auto;
  overflow-y: visible;
  background: #f8fbff;
  border: 1px solid #e4ecf5;
  border-radius: 8px;
  text-align: left;
  line-height: 1.9;
}

.rendered-message :deep(.katex) {
  font-size: 1.04em;
  white-space: nowrap;
  line-height: 1.55;
}

.rendered-message :deep(.katex-html) {
  max-width: none;
}

.rendered-message :deep(.katex-display > .katex) {
  display: inline-block;
  min-width: max-content;
  white-space: nowrap;
  padding: 2px 0;
}

.rendered-message :deep(:not(.katex-display) > .katex) {
  display: inline-flex;
  align-items: baseline;
  max-width: 100%;
  overflow-x: auto;
  overflow-y: visible;
  vertical-align: -0.12em;
  padding: 0 1px;
}

.rendered-message :deep(.math-fallback) {
  color: #9f1239;
  background: #fff1f2;
  border: 1px solid #fecdd3;
  border-radius: 6px;
}

.message-row.user .rendered-message :deep(h1),
.message-row.user .rendered-message :deep(h2),
.message-row.user .rendered-message :deep(h3),
.message-row.user .rendered-message :deep(a) {
  color: #ffffff;
}

.message-row.user .rendered-message :deep(h1) {
  border-bottom-color: rgba(255, 255, 255, 0.28);
}

.message-row.user .rendered-message :deep(code) {
  background: rgba(255, 255, 255, 0.16);
  color: #ffffff;
}

.message-row.user .rendered-message :deep(blockquote),
.message-row.user .rendered-message :deep(.katex-display) {
  background: rgba(255, 255, 255, 0.12);
  border-color: rgba(255, 255, 255, 0.22);
  color: #ffffff;
}

.message-row.user .rendered-message :deep(th),
.message-row.user .rendered-message :deep(td) {
  border-color: rgba(255, 255, 255, 0.25);
}

.message-row.user .rendered-message :deep(th) {
  background: rgba(255, 255, 255, 0.14);
  color: #ffffff;
}

.loading-bubble {
  color: #526276;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 28px;
  animation: thinkingPulse 1.8s ease-in-out infinite;
}

.thinking-text {
  white-space: nowrap;
}

.thinking-dots {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 14px;
}

.thinking-dots span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #7a8798;
  opacity: 0.35;
  transform: translateY(2px) scale(0.9);
  animation: thinkingDot 1.15s ease-in-out infinite;
}

.thinking-dots span:nth-child(2) {
  animation-delay: 0.16s;
}

.thinking-dots span:nth-child(3) {
  animation-delay: 0.32s;
}

/* 加一层很轻的呼吸效果，让等待状态更接近真实对话产品，而不是静态提示文案。 */
@keyframes thinkingPulse {
  0%,
  100% {
    box-shadow: 0 0 0 rgba(15, 139, 95, 0);
  }
  50% {
    box-shadow: 0 8px 24px rgba(15, 139, 95, 0.08);
  }
}

@keyframes thinkingDot {
  0%,
  80%,
  100% {
    opacity: 0.35;
    transform: translateY(2px) scale(0.9);
  }
  40% {
    opacity: 1;
    transform: translateY(-2px) scale(1);
  }
}

.source-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.source-list span {
  border-radius: 999px;
  background: #e2f4ff;
  color: #1477a8;
  padding: 5px 10px;
}

.quick-prompts {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}

.quick-prompts button {
  border: 1px solid #d8e2ef;
  background: #ffffff;
  color: #526276;
  border-radius: 999px;
  padding: 8px 14px;
}

.ask-box {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 52px;
  gap: 12px;
}

.ask-box input {
  height: 52px;
  border: 1px solid #d8e2ef;
  border-radius: 8px;
  padding: 0 16px;
  font-size: 16px;
}

.ask-box button {
  border: 0;
  border-radius: 8px;
  background: #05865f;
  color: #ffffff;
}

@media (max-width: 1100px) {
  .study-shell {
    grid-template-columns: 1fr;
  }
}
</style>
