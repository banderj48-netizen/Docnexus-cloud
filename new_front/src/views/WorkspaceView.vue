<template>
  <StudioLayout>
    <header class="studio-header">
      <div class="headline">
        <span class="eyebrow">文枢智能 DocNexus</span>
        <h1>文档库与知识库工作台</h1>
        <p>上传资料、跟踪 AI 解析状态、沉淀可检索知识片段，并在 AI 阅读室中围绕资料进行问答和整理。</p>
      </div>

      <div class="header-actions">
        <button class="tool-button" title="全局搜索" type="button"><Search /></button>
        <button class="tool-button" title="消息通知" type="button"><Bell /></button>
        <button class="primary-button" type="button" @click="openFilePicker">
          <Upload />
          {{ uploadButtonText }}
        </button>
      </div>
    </header>

    <section class="flow-strip" aria-label="知识库工作流">
      <article v-for="stage in workflowStages" :key="stage.title" class="flow-stage" :class="stage.state">
        <div class="stage-icon"><component :is="stage.icon" /></div>
        <div>
          <span>{{ stage.kicker }}</span>
          <strong>{{ stage.title }}</strong>
          <p>{{ stage.metric }}</p>
        </div>
      </article>
    </section>

    <section class="workspace-board knowledge-workspace-board">
      <section class="surface workspace-data-panel">
        <div class="surface-header">
          <div>
            <h2>文档库</h2>
            <p>统一管理 PDF、Word、PPT、Markdown 等资料。文件上传后进入解析、摘要、关键词提取和向量索引流程。</p>
          </div>
          <button class="secondary-button" type="button" @click="router.push('/knowledge')">
            <FolderOpened />
            查看全部
          </button>
        </div>

        <div class="understanding-layout">
          <div
            class="upload-drop"
            :class="{ dragging: isDragging, uploading: isUploading }"
            role="button"
            tabindex="0"
            @click="openFilePicker"
            @keydown.enter.prevent="openFilePicker"
            @keydown.space.prevent="openFilePicker"
            @dragenter.prevent="handleDragEnter"
            @dragover.prevent="handleDragEnter"
            @dragleave.prevent="handleDragLeave"
            @drop.prevent="handleDrop"
          >
            <UploadFilled />
            <strong>拖入资料文件</strong>
            <span>{{ uploadHint }}</span>
            <button type="button" @click.stop="openFilePicker">{{ uploadButtonText }}</button>
            <input
              ref="fileInputRef"
              class="hidden-file-input"
              type="file"
              multiple
              accept=".pdf,.doc,.docx,.ppt,.pptx,.md,.markdown,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.ms-powerpoint,application/vnd.openxmlformats-officedocument.presentationml.presentation,text/markdown,text/plain"
              @change="handleFileChange"
            />
          </div>

          <div class="insight-board">
            <div class="insight-card main-insight">
              <span>知识库状态</span>
              <strong>{{ indexedFileCount }} / {{ files.length }} 份资料已索引</strong>
              <p>{{ knowledgeSummary }}</p>
            </div>
            <div class="keyword-cloud">
              <span v-for="keyword in keywords" :key="keyword">{{ keyword }}</span>
            </div>
          </div>
        </div>

        <div class="file-list">
          <div class="file-list-head">
            <span>资料名称</span>
            <span>索引进度</span>
            <span>状态</span>
          </div>
          <div v-for="file in recentFiles" :key="file.id || file.fileId || file.name" class="file-row">
            <div class="file-name">
              <component :is="resolveFileIcon(file)" />
              <div>
                <strong>{{ file.filename || file.fileName || file.name }}</strong>
                <small>{{ file.meta || formatFileMeta(file) }}</small>
              </div>
            </div>
            <div class="understand-meter">
              <span :style="{ width: fileUnderstandProgress(file) + '%' }" />
            </div>
            <span class="file-purpose">{{ fileStatusLabel(file) }}</span>
          </div>
          <div v-if="!recentFiles.length" class="empty-state">暂无资料数据</div>
        </div>
      </section>

      <section class="surface study-room-panel">
        <div class="surface-header compact">
          <div>
            <h2>AI 阅读室</h2>
            <p>只围绕文档库和知识库进行资料问答、摘要、追问和引用溯源。</p>
          </div>
          <button class="secondary-button" type="button" @click="router.push('/study')">
            <ChatDotRound />
            进入
          </button>
        </div>

        <div class="study-room-card">
          <ChatDotRound />
          <strong>{{ files.length ? '开始基于资料提问' : '等待上传资料' }}</strong>
          <p>
            {{ files.length
              ? 'AI 阅读室会优先使用已索引资料回答问题，并展示引用来源。'
              : '先上传资料，系统完成解析后即可进行 RAG 问答。' }}
          </p>
        </div>

        <div class="study-room-actions">
          <button type="button" @click="router.push('/study')">资料问答</button>
          <button type="button" @click="createPresetTodo(presets[0])">总结资料</button>
          <button type="button" @click="createPresetTodo(presets[1])">提取关键词</button>
        </div>
      </section>

      <section class="surface todo-console">
        <div class="surface-header compact">
          <div>
            <h2>AI 日志</h2>
            <p>查看最近 AI 调用、RAG 召回、工具执行和上下文消耗。</p>
          </div>
          <button class="secondary-button" type="button" @click="router.push('/ai-logs')">
            <Tickets />
            日志
          </button>
        </div>

        <form class="workspace-todo-form" @submit.prevent="createWorkspaceTodo">
          <input v-model.trim="todoDraft" placeholder="例如：总结本周上传资料的核心观点" />
          <button title="发起 AI 处理" type="submit"><Plus /></button>
        </form>

        <div class="workspace-todo-list">
          <article
            v-for="todo in workspaceTodos"
            :key="todo.id"
            class="workspace-todo-item"
            :class="{ selected: selectedTodo?.id === todo.id }"
            @click="selectTodo(todo)"
          >
            <div>
              <strong>{{ todo.title }}</strong>
              <span>{{ statusLabel(todo.status) }} · {{ typeLabel(todo.type) }}</span>
            </div>
            <button type="button" title="执行 AI 处理" :disabled="todo.status === 'running'" @click.stop="runWorkspaceTodo(todo)">
              <VideoPlay />
            </button>
            <button class="danger-icon-button" type="button" title="删除任务" @click.stop="removeWorkspaceTodo(todo)">
              <Delete />
            </button>
          </article>
          <div v-if="!workspaceTodos.length" class="empty-state">暂无 AI 处理记录</div>
        </div>
      </section>

      <section class="surface production-panel">
        <div class="surface-header">
          <div>
            <h2>AI 处理轨迹</h2>
            <p>展示当前 AI 调用的检索、摘要、关键词提取和引用检查步骤。</p>
          </div>
          <button class="secondary-button" type="button" @click="router.push('/knowledge')">
            <DataAnalysis />
            文档库
          </button>
        </div>

        <div class="production-layout refined">
          <div class="todo-brief-card">
            <template v-if="selectedTodo">
              <span class="todo-brief-label">当前 AI 处理</span>
              <strong>{{ selectedTodo.title }}</strong>
              <p>{{ selectedTodo.instruction }}</p>
              <div class="todo-brief-meta">
                <span>{{ typeLabel(selectedTodo.type) }}</span>
                <span>{{ selectedTodo.sources || '全部资料' }}</span>
                <span>{{ statusLabel(selectedTodo.status) }}</span>
              </div>
              <label class="pipeline-detail-editor">
                <span>补充检索范围与阅读重点</span>
                <textarea
                  v-model="detailDraft"
                  :disabled="selectedTodo.status === 'running'"
                  placeholder="补充需要重点阅读的资料范围、章节、关键词、引用要求或输出长度。"
                />
              </label>
              <div class="brief-actions refined-actions">
                <button type="button" :disabled="selectedTodo.status === 'running' || isSavingDetail" @click="saveTodoDetail">
                  <Select />
                  {{ isSavingDetail ? '保存中' : '保存说明' }}
                </button>
                <button type="button" :disabled="selectedTodo.status === 'running'" @click="runWorkspaceTodo(selectedTodo)">
                  <VideoPlay />
                  {{ selectedTodo.status === 'running' ? '执行中' : '执行任务' }}
                </button>
              </div>
            </template>
            <div v-else class="empty-state">暂无可执行 AI 处理</div>

            <div class="todo-template-row">
              <button v-for="preset in presets" :key="preset.title" type="button" @click="createPresetTodo(preset)">
                {{ preset.title }}
              </button>
            </div>
          </div>

          <div class="agent-run">
            <div v-for="event in agentEvents" :key="event.id || event.title" class="agent-event" :class="event.state">
              <span class="event-dot" />
              <div>
                <strong>{{ event.title }}</strong>
                <p>{{ event.detail }}</p>
              </div>
              <component :is="resolveStepIcon(event.iconType)" />
            </div>
            <div v-if="!agentEvents.length" class="empty-state">当前任务暂无处理轨迹</div>
          </div>
        </div>
      </section>

      <section class="surface deliverable-panel knowledge-health-panel">
        <div class="surface-header compact">
          <div>
            <h2>知识库健康</h2>
            <p>关注解析进度、索引覆盖、任务队列和可用性趋势。</p>
          </div>
        </div>

        <div class="review-grid">
          <div>
            <span>已索引资料</span>
            <strong>{{ indexedFileCount }}</strong>
          </div>
          <div>
            <span>解析中资料</span>
            <strong>{{ indexingFileCount }}</strong>
          </div>
          <div>
            <span>待执行任务</span>
            <strong>{{ pendingTodoCount }}</strong>
          </div>
          <div>
            <span>任务完成数</span>
            <strong>{{ doneTodoCount }}</strong>
          </div>
        </div>
      </section>
    </section>
  </StudioLayout>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Bell,
  ChatDotRound,
  DataAnalysis,
  DataBoard,
  Delete,
  Document,
  Files,
  FolderOpened,
  Notebook,
  Plus,
  Search,
  Select,
  Tickets,
  Upload,
  UploadFilled,
  VideoPlay,
} from '@element-plus/icons-vue'
import StudioLayout from '../components/StudioLayout.vue'
import { STORAGE_KEYS } from '../constants'
import { aiTodoApi } from '../api/aiTodo'
import { fileApi } from '../api/file'
import { workspaceApi } from '../api/workspace'
import { filterDeletingFiles, subscribeFileDeleting } from '../utils/deletedFiles'
import { getUserIdFromToken, getUsernameFromToken, getUserRole } from '../utils/jwt'

const router = useRouter()
const fileInputRef = ref(null)
const isDragging = ref(false)
const isUploading = ref(false)
const uploadProgress = ref(null)
const files = ref([])
const agentEvents = ref([])
const workspaceTodos = ref([])
const selectedTodoId = ref('')
const todoDraft = ref('')
const detailDraft = ref('')
const isSavingDetail = ref(false)
let understandingPollTimer = null
let unsubscribeFileDeleting = null

const presets = [
  {
    title: '总结资料',
    type: 'summary',
    instruction: '总结当前资料的核心观点、关键词、重要结论和可引用来源。',
  },
  {
    title: '提取关键词',
    type: 'keywords',
    instruction: '从当前知识库中提取高频关键词、主题方向和相关资料来源。',
  },
  {
    title: '引用检查',
    type: 'review',
    instruction: '检查已有资料问答结果是否具备引用来源，并标记缺少依据的结论。',
  },
]

const keywords = ['RAG', '文档解析', '向量检索', '资料摘要', '关键词', '引用溯源', 'Qdrant', 'MinIO']

const currentUser = ref({})

const loadCurrentUser = () => {
  const token = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN)
  let storedUser = {}
  try {
    storedUser = JSON.parse(localStorage.getItem(STORAGE_KEYS.USER_INFO) || '{}')
  } catch {
    storedUser = {}
  }

  const userId = storedUser.id || storedUser.userId || getUserIdFromToken(token)
  const username = storedUser.username || getUsernameFromToken(token) || localStorage.getItem('userName') || ''
  const role = storedUser.role || getUserRole() || ''

  currentUser.value = {
    ...storedUser,
    id: userId,
    userId,
    username,
    role,
  }

  if (userId || username || role) {
    localStorage.setItem(STORAGE_KEYS.USER_INFO, JSON.stringify(currentUser.value))
  }
}

loadCurrentUser()

const username = computed(() => currentUser.value.username || '未登录用户')
const userInitial = computed(() => username.value.trim().charAt(0).toUpperCase() || '用')
const userSubtitle = computed(() => currentUser.value.displayName || currentUser.value.role || '当前用户')
const goUserProfile = () => {
  router.push('/profile')
}
const selectedTodo = computed(() => workspaceTodos.value.find((todo) => todo.id === selectedTodoId.value) || null)
const recentFiles = computed(() => files.value.slice(0, 6))
const indexedFileCount = computed(() => files.value.filter((file) => normalizedUnderstandStatus(file) === 'INDEXED').length)
const indexingFileCount = computed(() => files.value.filter((file) => ['PENDING', 'INDEXING'].includes(normalizedUnderstandStatus(file))).length)
const pendingTodoCount = computed(() => workspaceTodos.value.filter((todo) => todo.status === 'pending').length)
const doneTodoCount = computed(() => workspaceTodos.value.filter((todo) => todo.status === 'done').length)
const uploadButtonText = computed(() => (isUploading.value ? '上传中...' : '上传资料'))
const uploadHint = computed(() => {
  if (uploadProgress.value) return `${uploadProgress.value.name}，${uploadProgress.value.percent}%`
  return isUploading.value ? '正在上传文件，请稍候。' : '20MB 内直传，20MB 到 100MB 自动分片上传。'
})
const knowledgeSummary = computed(() => {
  if (!files.value.length) return '当前文档库为空，请先上传资料。'
  if (indexedFileCount.value === files.value.length) return '全部资料均已完成解析和索引，可以稳定用于 RAG 问答。'
  return '仍有资料处于待解析或解析中，索引完成后会自动提升问答覆盖率。'
})

const workflowStages = computed(() => [
  { kicker: 'Step 01', title: '文档入库', metric: `${files.value.length} 份资料`, state: files.value.length ? 'done' : 'waiting', icon: UploadFilled },
  { kicker: 'Step 02', title: '解析摘要', metric: `${indexingFileCount.value} 份处理中`, state: indexingFileCount.value ? 'active' : 'waiting', icon: Document },
  { kicker: 'Step 03', title: '向量索引', metric: `${indexedFileCount.value} 份已索引`, state: indexedFileCount.value ? 'done' : 'waiting', icon: DataAnalysis },
  { kicker: 'Step 04', title: '资料问答', metric: 'AI 阅读室', state: files.value.length ? 'active' : 'waiting', icon: ChatDotRound },
  { kicker: 'Step 05', title: 'AI 日志', metric: `${workspaceTodos.value.length} 条记录`, state: workspaceTodos.value.length ? 'active' : 'waiting', icon: Tickets },
])

const fileIconMap = {
  pdf: Files,
  word: Document,
  doc: Document,
  docx: Document,
  ppt: DataBoard,
  pptx: DataBoard,
  markdown: Notebook,
  md: Notebook,
}
const stepIconMap = { document: Document, search: Search, magic: DataAnalysis, upload: Upload }

const typeLabel = (type) => ({ summary: '资料摘要', rag: '资料问答', keywords: '关键词提取', review: '引用检查' }[type] || 'AI 处理')
const statusLabel = (status) => ({ pending: '待执行', running: '执行中', done: '已完成', failed: '失败' }[status] || '待执行')
const normalizedUnderstandStatus = (file) => String(file?.understandStatus || file?.status || '').toUpperCase()
const fileUnderstandProgress = (file) => Number(file?.understandProgress ?? file?.understandScore ?? file?.score ?? 0)
const fileStatusLabel = (file) => {
  const statusMap = {
    PENDING: '待解析',
    INDEXING: '解析中',
    INDEXED: '已索引',
    DONE: '已上传',
    PARSED: '已解析',
    UPLOADING: '上传中',
    FAILED: file?.ragError ? '解析失败' : '失败',
  }
  return statusMap[normalizedUnderstandStatus(file)] || '待解析'
}
const resolveStepIcon = (iconType) => stepIconMap[iconType] || DataAnalysis
const resolveFileIcon = (file) => {
  const name = file?.fileName || file?.filename || file?.name || ''
  const suffix = name.includes('.') ? name.split('.').pop().toLowerCase() : ''
  return fileIconMap[file?.iconType] || fileIconMap[suffix] || Files
}

const formatFileSize = (size) => {
  if (!size) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let value = Number(size)
  let index = 0
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024
    index += 1
  }
  return `${value.toFixed(value >= 10 || index === 0 ? 0 : 1)} ${units[index]}`
}

const formatFileMeta = (file) => `${file.fileType || file.type || '未知类型'} / ${formatFileSize(file.fileSize)}`
const supportedExtensions = ['pdf', 'doc', 'docx', 'ppt', 'pptx', 'md', 'markdown']
const isSupportedFile = (file) => supportedExtensions.includes(file.name.includes('.') ? file.name.split('.').pop().toLowerCase() : '')

const openFilePicker = () => {
  if (!isUploading.value) fileInputRef.value?.click()
}

const uploadSelectedFiles = async (fileList) => {
  const selectedFiles = Array.from(fileList || [])
  const validFiles = selectedFiles.filter(isSupportedFile)
  if (!selectedFiles.length || isUploading.value) return
  if (!validFiles.length) {
    ElMessage.warning('请选择 PDF、Word、PPT 或 Markdown 文件')
    return
  }
  if (validFiles.length !== selectedFiles.length) ElMessage.warning('已跳过不支持的文件类型')

  isUploading.value = true
  uploadProgress.value = null
  try {
    for (const file of validFiles) {
      await fileApi.upload(file, {
        onProgress: (progress) => {
          uploadProgress.value = {
            name: progress.file?.name || file.name,
            percent: progress.percent,
          }
        },
      })
    }
    ElMessage.success(`已上传 ${validFiles.length} 个文件`)
    await loadWorkspaceData()
  } catch (error) {
    console.warn('文件上传失败', error)
    ElMessage.error(error?.message || '文件上传失败')
  } finally {
    isUploading.value = false
    isDragging.value = false
    uploadProgress.value = null
    if (fileInputRef.value) fileInputRef.value.value = ''
  }
}

const handleFileChange = (event) => uploadSelectedFiles(event.target.files)
const handleDragEnter = () => {
  if (!isUploading.value) isDragging.value = true
}
const handleDragLeave = (event) => {
  if (!event.currentTarget.contains(event.relatedTarget)) isDragging.value = false
}
const handleDrop = (event) => {
  isDragging.value = false
  uploadSelectedFiles(event.dataTransfer.files)
}

const pickDefaultTodo = (todos) => todos.find((todo) => todo.status === 'running') || todos[0] || null

const loadTodoSteps = async (todoId) => {
  if (!todoId) {
    agentEvents.value = []
    return
  }
  const response = await aiTodoApi.steps(todoId)
  agentEvents.value = Array.isArray(response.data) ? response.data : []
}

const selectTodo = async (todo) => {
  selectedTodoId.value = todo.id
  detailDraft.value = todo.detailInstruction || ''
  await loadTodoSteps(todo.id)
}

const loadWorkspaceData = async () => {
  try {
    const [fileRes, todoRes] = await Promise.all([
      workspaceApi.listKnowledgeFiles(),
      aiTodoApi.list(),
    ])
    files.value = filterDeletingFiles(fileRes.data)
    workspaceTodos.value = Array.isArray(todoRes.data) ? todoRes.data.slice(0, 6) : []
    const defaultTodo = pickDefaultTodo(workspaceTodos.value)
    selectedTodoId.value = defaultTodo?.id || ''
    detailDraft.value = defaultTodo?.detailInstruction || ''
    await loadTodoSteps(selectedTodoId.value)
    syncUnderstandingPolling()
  } catch (error) {
    console.warn('工作台数据加载失败，展示空数据', error)
    files.value = []
    agentEvents.value = []
    workspaceTodos.value = []
  }
}

const hasRunningUnderstanding = () => files.value.some((file) => ['PENDING', 'INDEXING'].includes(normalizedUnderstandStatus(file)))

const syncUnderstandingPolling = () => {
  if (!hasRunningUnderstanding()) {
    if (understandingPollTimer) {
      window.clearInterval(understandingPollTimer)
      understandingPollTimer = null
    }
    return
  }
  if (understandingPollTimer) return
  understandingPollTimer = window.setInterval(loadWorkspaceData, 2500)
}

const createTodo = async ({ title, instruction, type = 'summary', sources = '' }) => {
  const response = await aiTodoApi.create({
    title,
    instruction,
    type,
    priority: 'normal',
    sources,
    detailInstruction: '',
    status: 'pending',
  })
  workspaceTodos.value.unshift(response.data)
  workspaceTodos.value = workspaceTodos.value.slice(0, 6)
  await selectTodo(response.data)
  return response.data
}

const createWorkspaceTodo = async () => {
  if (!todoDraft.value) {
    ElMessage.warning('请先填写 AI 处理内容')
    return
  }
  await createTodo({ title: todoDraft.value, instruction: todoDraft.value })
  todoDraft.value = ''
  ElMessage.success('已创建 AI 处理')
}

const createPresetTodo = async (preset) => {
  await createTodo(preset)
  ElMessage.success(`已创建「${preset.title}」`)
}

const saveTodoDetail = async ({ silent = false } = {}) => {
  const todo = selectedTodo.value
  if (!todo) return null
  if (todo.status === 'running') {
    if (!silent) ElMessage.warning('执行中的任务暂不能修改补充说明')
    return todo
  }
  isSavingDetail.value = true
  try {
    const response = await aiTodoApi.update(todo.id, {
      ...todo,
      detailInstruction: detailDraft.value,
    })
    Object.assign(todo, response.data)
    detailDraft.value = todo.detailInstruction || ''
    if (!silent) ElMessage.success('补充说明已保存')
    return todo
  } finally {
    isSavingDetail.value = false
  }
}

const runWorkspaceTodo = async (todo) => {
  if (todo.status !== 'running' && selectedTodoId.value === todo.id) {
    await saveTodoDetail({ silent: true })
  }
  const response = await aiTodoApi.run(todo.id)
  Object.assign(todo, response.data)
  await selectTodo(todo)
  ElMessage.success('已发送给 AI 执行队列')
}

const removeWorkspaceTodo = async (todo) => {
  if (todo.status === 'running') {
    ElMessage.warning('执行中的任务暂不能删除')
    return
  }
  if (!window.confirm(`确定删除「${todo.title}」吗？`)) return
  await aiTodoApi.remove(todo.id)
  workspaceTodos.value = workspaceTodos.value.filter((item) => item.id !== todo.id)
  if (selectedTodoId.value === todo.id) {
    const nextTodo = pickDefaultTodo(workspaceTodos.value)
    selectedTodoId.value = nextTodo?.id || ''
    detailDraft.value = nextTodo?.detailInstruction || ''
    await loadTodoSteps(selectedTodoId.value)
  }
  ElMessage.success('AI 处理记录已删除')
}

onMounted(() => {
  loadCurrentUser()
  unsubscribeFileDeleting = subscribeFileDeleting((fileId) => {
    files.value = files.value.filter((file) => String(file.fileId || file.id || '') !== fileId)
  })
  loadWorkspaceData()
})
onUnmounted(() => {
  if (understandingPollTimer) window.clearInterval(understandingPollTimer)
  unsubscribeFileDeleting?.()
})
</script>
