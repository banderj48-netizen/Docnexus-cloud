<template>
  <StudioLayout>
    <div class="nexus-page">
    <main class="nexus-main">
      <section class="hero-band">
        <div class="hero-copy">
          <span class="hero-eyebrow">Knowledge Library & Map</span>
          <h1>文档库与知识图谱</h1>
          <p>集中管理上传文档，调用 Agent 自动解析、切片、向量化与图谱构建，为后续问答 Agent 提供可用知识。</p>
        </div>
        <div class="hero-actions">
          <button class="ghost-button" type="button" @click="openFilePicker">
            <Upload />
            批量导入
          </button>
          <button class="solid-button" type="button" @click="openFilePicker">
            <UploadFilled />
            上传资料
          </button>
        </div>
        <input
          ref="fileInputRef"
          class="hidden-input"
          type="file"
          multiple
          accept=".pdf,.doc,.docx,.ppt,.pptx,.txt,.jpg,.jpeg,.png,.webp,.bmp,.tif,.tiff,.gif"
          @change="handleFileChange"
        />
      </section>

      <section class="metric-grid" aria-label="文档库统计">
        <article v-for="metric in metrics" :key="metric.label" class="metric-card">
          <span class="metric-icon" :class="metric.tone">
            <component :is="metric.icon" />
          </span>
          <div>
            <p>{{ metric.label }}</p>
            <strong>{{ metric.value }}</strong>
            <small>{{ metric.desc }}</small>
          </div>
        </article>
      </section>

      <section class="workbench-grid">
        <section class="panel upload-panel">
          <div class="panel-heading">
            <div>
              <h2><UploadFilled /> 上传与处理中心</h2>
              <p>上传文档后，系统将自动进入 Agent 解析流水线。</p>
            </div>
          </div>

          <button class="drop-zone" type="button" @click="openFilePicker">
            <Upload />
            <strong>拖拽文件到此处，或点击上传</strong>
            <span>支持 PDF、DOCX、PPTX、XLSX、TXT 等格式，单个文件 ≤ 200MB</span>
          </button>

          <div class="table-block">
            <div class="section-title">
              <h3>处理队列</h3>
              <div class="queue-filter" aria-label="处理队列筛选">
                <button
                  v-for="item in queueFilters"
                  :key="item.value"
                  type="button"
                  :class="{ active: activeQueueFilter === item.value }"
                  @click="activeQueueFilter = item.value"
                >
                  {{ item.label }}
                </button>
              </div>
            </div>

            <div class="data-table queue-table">
              <div class="table-row table-head">
                <span>文档名称</span>
                <span>大小</span>
                <span>上传时间</span>
                <span>当前阶段</span>
                <span>进度</span>
                <span>操作</span>
              </div>
              <div v-for="file in filteredQueueFiles" :key="file.id" class="table-row" :class="[`queue-${file.status}`]">
                <span class="file-cell">
                  <i :class="['file-type', file.type.toLowerCase()]">{{ file.type }}</i>
                  <span class="file-copy">
                    <strong>{{ file.name }}</strong>
                    <small v-if="file.errorMessage">{{ file.errorMessage }}</small>
                  </span>
                </span>
                <span>{{ file.size }}</span>
                <span>{{ file.time }}</span>
                <span>
                  <em class="status-pill" :class="file.statusTone">{{ file.stage }}</em>
                </span>
                <span class="progress-cell" :class="file.status">
                  <strong>{{ file.progress }}%</strong>
                  <i><b :style="{ width: `${file.progress}%` }"></b></i>
                </span>
                <span class="row-actions">
                  <template v-if="file.status === 'failed'">
                    <button class="text-action primary" type="button" title="重新上传" @click="retryQueueFile(file)">重新上传</button>
                    <button class="text-action danger" type="button" title="取消" @click="cancelQueueFile(file)">取消</button>
                  </template>
                  <template v-else-if="file.status === 'interrupted'">
                    <button class="text-action primary" type="button" title="继续上传" @click="continueInterruptedUpload(file)">继续上传</button>
                    <button class="text-action danger" type="button" title="取消" @click="cancelQueueFile(file)">取消</button>
                  </template>
                  <template v-else>
                  <button type="button" :title="file.paused ? '继续处理' : '暂停处理'">
                    <VideoPlay v-if="file.paused" />
                    <VideoPause v-else />
                  </button>
                  <button type="button" title="取消任务" @click="cancelQueueFile(file)">
                    <Close />
                  </button>
                  </template>
                </span>
              </div>
            </div>
          </div>

          <div class="table-block library-block">
            <h3>已上传文档</h3>
            <div class="data-table library-table">
              <div class="table-row table-head">
                <span>文档名称</span>
                <span>类型</span>
                <span>上传时间</span>
                <span>状态</span>
                <span>知识库</span>
                <span>图谱</span>
                <span>操作</span>
              </div>
              <div v-for="file in libraryFiles" :key="file.id" class="table-row">
                <span class="file-cell">
                  <i :class="['file-type', file.type.toLowerCase()]">{{ file.type }}</i>
                  <strong>{{ file.name }}</strong>
                </span>
                <span>{{ file.type }}</span>
                <span>{{ file.time }}</span>
                <span><em class="status-pill" :class="file.statusTone">{{ file.status }}</em></span>
                <span class="state-cell" :class="file.knowledgeTone">
                  <CircleCheck />
                  {{ file.knowledge }}
                </span>
                <span class="state-cell" :class="file.graphTone">
                  <Share />
                  {{ file.graph }}
                </span>
                <span class="row-actions">
                  <button type="button" title="预览文档" @click="previewFile(file)"><View /></button>
                  <button type="button" title="下载文档" @click="downloadFile(file)"><Download /></button>
                  <button type="button" title="更多操作"><MoreFilled /></button>
                </span>
              </div>
            </div>
            <footer class="table-footer">
              <span>共 {{ libraryTotal }} 条</span>
              <button type="button" disabled><ArrowLeft /></button>
              <button class="page-current" type="button">1</button>
              <button type="button"><ArrowRight /></button>
              <select aria-label="每页条数">
                <option>10 条/页</option>
                <option>20 条/页</option>
              </select>
            </footer>
          </div>
        </section>

        <aside class="right-stack">
          <section class="panel graph-panel">
            <div class="panel-heading inline">
              <h2><Connection /> 知识图谱概览</h2>
              <button class="mini-button" type="button">查看完整图谱</button>
            </div>
            <div class="graph-content">
              <div class="graph-stats">
                <div>
                  <small><DataLine /> 主题簇</small>
                  <strong>26</strong>
                </div>
                <div>
                  <small><Aim /> 核心实体</small>
                  <strong>1,280</strong>
                </div>
                <div>
                  <small><Connection /> 高维关系</small>
                  <strong>3,420</strong>
                </div>
              </div>
              <svg class="knowledge-graph" viewBox="0 0 440 250" role="img" aria-label="知识图谱示意">
                <g class="graph-lines">
                  <line v-for="edge in graphEdges" :key="edge.id" :x1="edge.x1" :y1="edge.y1" :x2="edge.x2" :y2="edge.y2" />
                </g>
                <g v-for="node in graphNodes" :key="node.label" class="graph-node">
                  <circle :cx="node.x" :cy="node.y" :r="node.main ? 24 : 8" :fill="node.color" />
                  <text :x="node.x" :y="node.y + (node.main ? 5 : -14)" text-anchor="middle">{{ node.label }}</text>
                </g>
              </svg>
            </div>
            <div class="graph-legend">
              <span><i class="legend-topic"></i> 主题簇</span>
              <span><i class="legend-entity"></i> 实体</span>
              <span><i class="legend-edge"></i> 关系</span>
            </div>
          </section>

          <section class="panel pipeline-panel">
            <div class="panel-heading inline">
              <h2><Cpu /> Agent 解析流水线</h2>
            </div>
            <div class="pipeline-line">
              <article v-for="step in pipelineSteps" :key="step.name">
                <span><Check /></span>
                <strong>{{ step.name }}</strong>
                <small>{{ step.desc }}</small>
              </article>
            </div>
            <footer class="pipeline-footer">
              <span><CircleCheckFilled /> 流水线运行正常，平均处理时长 2分18秒</span>
              <button class="mini-button" type="button">查看流水线日志</button>
            </footer>
          </section>

          <section class="panel recent-panel">
            <div class="panel-heading inline">
              <h2><Finished /> 最近完成解析</h2>
              <button class="mini-button" type="button">查看全部</button>
            </div>
            <div class="recent-list">
              <article v-for="item in recentFiles" :key="item.name">
                <span class="file-cell">
                  <i :class="['file-type', item.type.toLowerCase()]">{{ item.type }}</i>
                  <strong>{{ item.name }}</strong>
                </span>
                <time>{{ item.time }}</time>
                <em><CircleCheckFilled /> {{ item.result }}</em>
              </article>
            </div>
          </section>
        </aside>
      </section>
    </main>
    </div>
  </StudioLayout>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import StudioLayout from '../components/StudioLayout.vue'
import { fileApi } from '../api/file'
import { STORAGE_KEYS } from '../constants'
import { notifyUserOperationChanged } from '../utils/sidebarStats'
import {
  Aim,
  ArrowLeft,
  ArrowRight,
  Check,
  CircleCheck,
  CircleCheckFilled,
  Close,
  Connection,
  Cpu,
  DataLine,
  Download,
  Files,
  Finished,
  Folder,
  MoreFilled,
  Operation,
  Share,
  Upload,
  UploadFilled,
  VideoPause,
  VideoPlay,
  View,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const fileInputRef = ref(null)
const activeQueueFilter = ref('all')
const libraryTotal = ref(0)
const uploading = ref(false)
const failedUploads = ref([])
const pendingRecoverRow = ref(null)
const maxFileSize = 200 * 1024 * 1024
const multipartThreshold = 100 * 1024 * 1024
const uploadDraftStorageKey = 'docnexus:file:upload:drafts'
const supportedExtensions = new Set([
  'pdf',
  'doc',
  'docx',
  'ppt',
  'pptx',
  'xls',
  'xlsx',
  'txt',
  'jpg',
  'jpeg',
  'png',
  'webp',
  'bmp',
  'tif',
  'tiff',
  'gif',
])

const queueFilters = [
  { label: '全部', value: 'all' },
  { label: '上传中', value: 'uploading' },
  { label: '待解析', value: 'waiting' },
  { label: '解析中', value: 'parsing' },
  { label: '已完成', value: 'done' },
  { label: '失败', value: 'failed' },
]

const metrics = computed(() => {
  const parsingCount = queueFiles.value.filter((file) => file.status === 'parsing').length
  return [
    { label: '资料总数', value: String(libraryTotal.value), desc: '已纳入统一管理的文档', icon: Folder, tone: 'green' },
    { label: '解析中任务', value: String(parsingCount), desc: 'Agent 正在处理的文档', icon: Operation, tone: 'blue' },
    { label: '知识库切片', value: '0', desc: '等待后续解析服务回写', icon: Files, tone: 'purple' },
    { label: '图谱实体 / 关系', value: '0 / 0', desc: '等待后续图谱构建回写', icon: Share, tone: 'mint' },
  ]
})

const queueFiles = ref([])

const libraryFiles = ref([])

const graphNodes = [
  { label: '产品', x: 230, y: 130, color: '#008d72', main: true },
  { label: '竞品对手', x: 160, y: 175, color: '#3b82f6' },
  { label: '用户', x: 105, y: 145, color: '#65c765' },
  { label: '法规', x: 285, y: 52, color: '#8b5cf6' },
  { label: '竞品', x: 150, y: 78, color: '#f59e0b' },
  { label: '需求', x: 352, y: 92, color: '#ef553d' },
  { label: '市场', x: 330, y: 152, color: '#f59e0b' },
  { label: '行业', x: 282, y: 186, color: '#65c765' },
  { label: '案例', x: 365, y: 188, color: '#94a3b8' },
  { label: '策略', x: 235, y: 35, color: '#3b82f6' },
]

const graphEdges = graphNodes
  .filter((node) => !node.main)
  .map((node, index) => ({ id: index, x1: 230, y1: 130, x2: node.x, y2: node.y }))

const pipelineSteps = [
  { name: '上传', desc: '文件接收' },
  { name: '文本抽取', desc: 'OCR / 识别' },
  { name: '切片', desc: '语义切分' },
  { name: '向量化', desc: '向量生成' },
  { name: '图谱构建', desc: '实体关系' },
]

const recentFiles = [
  { name: '招标文件.pdf', type: 'PDF', time: '06/01 09:48', result: '已入库并生成 126 个切片' },
  { name: '客户培训手册.docx', type: 'W', time: '06/01 09:21', result: '已构建知识图谱' },
  { name: '会议纪要_05-30.txt', type: 'TXT', time: '05/31 22:16', result: '已供问答 Agent 调用' },
]

const filteredQueueFiles = computed(() => {
  if (activeQueueFilter.value === 'all') return queueFiles.value
  return queueFiles.value.filter((file) => file.status === activeQueueFilter.value)
})

onMounted(() => {
  loadFileList()
  window.addEventListener('beforeunload', handlePageUnload)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handlePageUnload)
})

/**
 * 从后端加载已上传文档，并同步生成“待解析”处理队列。
 */
const loadFileList = async () => {
  const response = await fileApi.getFileList({ pageNum: 1, pageSize: 10, knowledgeBaseId: 'default' })
  const page = response.data || {}
  const records = (page.records || []).map(normalizeBackendFile)
  const uploadedFiles = records.filter((file) => file.uploadStatus === 'UPLOADED')
  const temporaryFiles = records.filter((file) => file.uploadStatus && file.uploadStatus !== 'UPLOADED')
  libraryFiles.value = uploadedFiles
  libraryTotal.value = Number(page.total || libraryFiles.value.length)
  queueFiles.value = temporaryFiles
    .map(createQueueRowFromLibrary)
    .concat(uploadedFiles
    .filter((file) => ['待解析', '解析中', '上传中', '待上传', '上传失败'].includes(file.knowledge) || file.status === '上传失败')
    .map(createQueueRowFromLibrary))
}

/**
 * 打开浏览器文件选择器，后续接入后端上传接口时仍复用该入口。
 */
const openFilePicker = () => {
  fileInputRef.value?.click()
}

/**
 * 将用户选择的文件按大小分流到普通上传或分片上传演示流程。
 */
const handleFileChange = (event) => {
  const files = Array.from(event.target.files || [])
  if (!files.length) return
  if (pendingRecoverRow.value) {
    continueUploadWithSelectedFile(pendingRecoverRow.value, files[0])
    pendingRecoverRow.value = null
    event.target.value = ''
    return
  }
  enqueueUploadFiles(files)
  event.target.value = ''
}

/**
 * 将批量选择的文件按顺序逐个上传，避免同时把大文件请求打到后端。
 */
const enqueueUploadFiles = async (files) => {
  if (uploading.value) {
    ElMessage.warning('已有文件正在上传，请稍后再试')
    return
  }
  uploading.value = true
  failedUploads.value = []

  try {
    for (const file of files) {
      const invalidReason = validateUploadFile(file)
      if (invalidReason) {
        failedUploads.value.push({ file, reason: invalidReason })
        continue
      }
      notifyUserOperationChanged()
      await uploadOneFile(file)
    }
  } finally {
    uploading.value = false
    await loadFileList()
  }
}

/**
 * 校验上传文件的大小和格式，保证前端先拦截明显不合法的资料。
 */
const validateUploadFile = (file) => {
  const extension = resolveFileExtension(file.name)
  if (!supportedExtensions.has(extension)) {
    return '仅支持 PDF、Word、PPT、TXT 和常见图片格式'
  }
  if (file.size <= 0) {
    return '不能上传空文件'
  }
  if (file.size > maxFileSize) {
    return '单个文件不能超过 200MB'
  }
  return ''
}

/**
 * 上传单个文件，并把进度实时写入当前页面的临时行。
 */
const uploadOneFile = async (file) => {
  const uploadMode = file.size > multipartThreshold ? 'multipart' : 'normal'
  const row = createLocalUploadRow(file, uploadMode)
  queueFiles.value = [createQueueRowFromLibrary(row), ...queueFiles.value]

  try {
    const response = await fileApi.upload(file, {
      knowledgeBaseId: 'default',
      onSession: ({ uploadId, totalChunks, chunkSize }) => {
        row.uploadId = uploadId
        row.totalChunks = totalChunks
        row.chunkSize = chunkSize
        saveUploadDraft(row, file)
        syncQueueRow(row)
      },
      onProgress: ({ percent, mode }) => {
        row.progress = percent
        row.status = mode === 'merge' ? '合并中' : uploadMode === 'multipart' ? '分片上传中' : '上传中'
        row.uploadStatus = mode === 'merge' ? 'COMPLETING' : 'UPLOADING'
        syncQueueRow(row)
      }
    })
    row.uploadId = response.data?.uploadId || row.uploadId
    const uploaded = normalizeBackendFile(response.data?.file)
    libraryFiles.value = [uploaded, ...libraryFiles.value]
    libraryTotal.value += 1
    queueFiles.value = [createQueueRowFromLibrary(uploaded), ...queueFiles.value.filter((item) => item.localId !== row.id && item.uploadId !== row.uploadId)]
    removeUploadDraft(row.uploadId)
    ElMessage.success(`${file.name} 已上传成功`)
  } catch (error) {
    row.status = '上传失败'
    row.uploadStatus = 'UPLOAD_FAILED'
    row.statusTone = 'red'
    row.progress = 0
    row.errorMessage = error?.message || '上传失败，请稍后重试'
    syncQueueRow(row)
    failedUploads.value.push({ file, row, reason: row.errorMessage })
  }
}

/**
 * 生成本地上传临时行。
 */
const createLocalUploadRow = (file, uploadMode) => ({
  id: `local_${Date.now()}_${Math.random().toString(16).slice(2)}`,
  fileId: '',
  uploadId: '',
  uploadStatus: uploadMode === 'multipart' ? 'UPLOADING' : 'UPLOADING',
  name: file.name,
  type: resolveFileType(file.name),
  size: formatFileSize(file.size),
  time: '刚刚',
  status: uploadMode === 'multipart' ? '分片上传中' : '上传中',
  statusTone: 'blue',
  knowledge: '待解析',
  knowledgeTone: 'waiting',
  graph: '待解析',
  graphTone: 'waiting',
  progress: 0,
  originalFile: file,
  lastModified: file.lastModified,
  fileSize: file.size,
  uploadMode,
})

/**
 * 基于已上传文档生成处理队列行。
 */
const createQueueRowFromLibrary = (row) => ({
  id: `task_${row.id}`,
  localId: row.id,
  fileId: row.id,
  uploadId: row.uploadId,
  uploadStatus: row.uploadStatus,
  name: row.name,
  type: row.type,
  size: row.size,
  time: row.time,
  stage: resolveQueueStage(row),
  status: resolveQueueStatus(row),
  statusTone: row.statusTone,
  progress: row.progress || 0,
  errorMessage: row.errorMessage || '',
  originalFile: row.originalFile,
  fileSize: row.fileSize,
  lastModified: row.lastModified,
  totalChunks: row.totalChunks,
  chunkSize: row.chunkSize,
  paused: false,
})

/**
 * 根据文档状态解析队列阶段。
 */
const resolveQueueStage = (row) => {
  if (row.uploadStatus === 'UPLOAD_FAILED' || row.status === '上传失败') return '处理失败'
  if (row.uploadStatus === 'INTERRUPTED' || row.status === '上传中断') return '上传中断'
  if (row.status !== '已上传' && (row.status.includes('上传') || row.status === '合并中')) return row.status
  if (row.knowledge === '解析中') return '解析中'
  if (row.knowledge === '已入库') return '已完成'
  return '待解析'
}

/**
 * 根据文档状态解析队列筛选状态。
 */
const resolveQueueStatus = (row) => {
  if (row.uploadStatus === 'UPLOAD_FAILED' || row.status === '上传失败') return 'failed'
  if (row.uploadStatus === 'INTERRUPTED' || row.status === '上传中断') return 'interrupted'
  if (row.status !== '已上传' && (row.status.includes('上传') || row.status === '合并中')) return 'uploading'
  if (row.knowledge === '解析中') return 'parsing'
  if (row.knowledge === '已入库') return 'done'
  return 'waiting'
}

/**
 * 把后端文件展示对象转换为当前页面表格字段。
 */
const normalizeBackendFile = (file = {}) => ({
  id: file.fileId || file.id || file.uploadId,
  fileId: file.fileId || file.id || '',
  uploadId: file.uploadId || '',
  uploadStatus: file.uploadStatus || 'UPLOADED',
  name: file.name || file.originalName || '未命名文档',
  type: file.type || resolveFileType(file.name || file.originalName || ''),
  size: file.sizeText || formatFileSize(file.fileSize),
  time: file.timeText || '刚刚',
  status: file.statusText || resolveUploadStatusText(file.uploadStatus),
  statusTone: file.statusTone || resolveStatusTone(file.uploadStatus),
  knowledge: file.knowledgeText || resolveKnowledgeText(file.parseStatus),
  knowledgeTone: file.knowledgeTone || resolveKnowledgeTone(file.parseStatus),
  graph: file.graphText || resolveGraphText(file.graphStatus),
  graphTone: file.graphTone || resolveGraphTone(file.graphStatus),
  progress: Number(file.progress || 0),
  errorMessage: file.errorMessage || '',
  fileSize: Number(file.fileSize || 0),
})

/**
 * 替换本地上传临时行。
 */
const replaceLibraryRow = (localId, uploaded) => {
  libraryFiles.value = libraryFiles.value.map((item) => item.id === localId ? uploaded : item)
}

/**
 * 同步上传临时行到处理队列。
 */
const syncQueueRow = (row) => {
  const nextRow = createQueueRowFromLibrary(row)
  queueFiles.value = queueFiles.value.map((item) => item.fileId === row.id ? nextRow : item)
}

/**
 * 重新上传失败队列行。
 */
const retryQueueFile = async (file) => {
  if (!file.originalFile) {
    ElMessage.warning('页面已刷新，请重新选择原文件上传')
    pendingRecoverRow.value = file
    openFilePicker()
    return
  }
  await cancelQueueFile(file, false)
  await enqueueUploadFiles([file.originalFile])
}

/**
 * 继续上传中断队列行。
 */
const continueInterruptedUpload = (file) => {
  pendingRecoverRow.value = file
  ElMessage.info('请选择同一个本地文件继续上传')
  openFilePicker()
}

/**
 * 使用用户重新选择的文件恢复分片上传。
 */
const continueUploadWithSelectedFile = async (row, file) => {
  if (!isSameRecoverFile(row, file)) {
    ElMessage.error('请选择与中断任务相同的文件')
    return
  }
  if (file.size <= multipartThreshold) {
    await cancelQueueFile(row, false)
    await enqueueUploadFiles([file])
    return
  }

  uploading.value = true
  row.originalFile = file
  row.status = 'uploading'
  row.stage = '上传中'
  row.statusTone = 'blue'
  row.errorMessage = ''
  notifyUserOperationChanged()
  try {
    const statusResponse = await fileApi.getChunkStatus(row.uploadId)
    const uploadedChunks = statusResponse.data?.uploadedChunkIndexes || []
    const response = await fileApi.uploadByChunks(file, {
      uploadId: row.uploadId,
      uploadedChunks,
      knowledgeBaseId: 'default',
      onProgress: ({ percent, mode }) => {
        row.progress = percent
        row.stage = mode === 'merge' ? '合并中' : '分片上传中'
        row.statusTone = 'blue'
      }
    })
    const uploaded = normalizeBackendFile(response.data?.file)
    libraryFiles.value = [uploaded, ...libraryFiles.value]
    libraryTotal.value += 1
    queueFiles.value = [createQueueRowFromLibrary(uploaded), ...queueFiles.value.filter((item) => item.uploadId !== row.uploadId)]
    removeUploadDraft(row.uploadId)
    ElMessage.success(`${file.name} 已继续上传完成`)
  } catch (error) {
    row.status = 'failed'
    row.stage = '处理失败'
    row.statusTone = 'red'
    row.errorMessage = error?.message || '继续上传失败，请稍后重试'
  } finally {
    uploading.value = false
  }
}

/**
 * 取消失败或中断队列行。
 */
const cancelQueueFile = async (file, showMessage = true) => {
  queueFiles.value = queueFiles.value.filter((item) => item.id !== file.id)
  if (file.uploadId) {
    removeUploadDraft(file.uploadId)
  }
  if (showMessage) {
    ElMessage.success('已取消上传任务')
  }
}

/**
 * 判断用户重新选择的文件是否匹配中断任务。
 */
const isSameRecoverFile = (row, file) => {
  return row.name === file.name && Number(row.fileSize || 0) === Number(file.size || 0)
}

/**
 * 浏览器刷新或关闭时尽力发送中断通知。
 */
const handlePageUnload = () => {
  const interruptedUploadIds = queueFiles.value
    .filter((file) => ['uploading', 'interrupted'].includes(file.status) && file.uploadId)
    .map((file) => file.uploadId)
  if (!interruptedUploadIds.length) return
  const token = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN) || ''
  const body = JSON.stringify({ uploadIds: interruptedUploadIds })
  fetch('/api/files/uploads/interrupt', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body,
    keepalive: true,
  }).catch(() => {})
}

/**
 * 保存上传草稿元数据，不保存文件内容。
 */
const saveUploadDraft = (row, file) => {
  if (!row.uploadId) return
  const drafts = readUploadDrafts()
  drafts[row.uploadId] = {
    uploadId: row.uploadId,
    fileName: file.name,
    fileSize: file.size,
    lastModified: file.lastModified,
    knowledgeBaseId: 'default',
  }
  localStorage.setItem(uploadDraftStorageKey, JSON.stringify(drafts))
}

/**
 * 删除上传草稿。
 */
const removeUploadDraft = (uploadId) => {
  if (!uploadId) return
  const drafts = readUploadDrafts()
  delete drafts[uploadId]
  localStorage.setItem(uploadDraftStorageKey, JSON.stringify(drafts))
}

/**
 * 读取上传草稿。
 */
const readUploadDrafts = () => {
  try {
    return JSON.parse(localStorage.getItem(uploadDraftStorageKey) || '{}')
  } catch {
    return {}
  }
}

/**
 * 预览文档。
 */
const previewFile = async (file) => {
  if (!file.fileId) return
  notifyUserOperationChanged()
  const blob = await fileApi.preview(file.fileId)
  const url = window.URL.createObjectURL(blob)
  window.open(url, '_blank', 'noopener')
  window.setTimeout(() => window.URL.revokeObjectURL(url), 30000)
}

/**
 * 下载文档。
 */
const downloadFile = async (file) => {
  if (!file.fileId) return
  notifyUserOperationChanged()
  const blob = await fileApi.download(file.fileId)
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = file.name
  link.click()
  window.URL.revokeObjectURL(url)
}

/**
 * 解析上传状态文案。
 */
const resolveUploadStatusText = (status) => {
  if (status === 'UPLOAD_FAILED') return '上传失败'
  if (status === 'INTERRUPTED') return '上传中断'
  if (status === 'UPLOADING') return '上传中'
  if (status === 'PENDING_UPLOAD') return '待上传'
  if (status === 'COMPLETING') return '合并中'
  return '已上传'
}

/**
 * 解析状态色调。
 */
const resolveStatusTone = (status) => {
  if (status === 'UPLOAD_FAILED') return 'red'
  if (status === 'INTERRUPTED') return 'amber'
  return status === 'UPLOADED' ? 'green' : 'blue'
}

/**
 * 解析知识库状态文案。
 */
const resolveKnowledgeText = (status) => {
  if (status === 'PROCESSING') return '解析中'
  if (status === 'SUCCESS') return '已入库'
  if (status === 'FAILED') return '解析失败'
  return '待解析'
}

/**
 * 解析知识库状态色调。
 */
const resolveKnowledgeTone = (status) => {
  if (status === 'PROCESSING') return 'running'
  if (status === 'SUCCESS') return 'ready'
  if (status === 'FAILED') return 'failed'
  return 'waiting'
}

/**
 * 解析图谱状态文案。
 */
const resolveGraphText = (status) => {
  if (status === 'BUILDING') return '构建中'
  if (status === 'SUCCESS') return '已构建'
  if (status === 'FAILED') return '构建失败'
  return '待解析'
}

/**
 * 解析图谱状态色调。
 */
const resolveGraphTone = (status) => {
  if (status === 'BUILDING') return 'running'
  if (status === 'SUCCESS') return 'ready'
  if (status === 'FAILED') return 'failed'
  return 'waiting'
}

/**
 * 获取文件名扩展名，用于上传白名单校验和类型标识生成。
 */
const resolveFileExtension = (fileName) => {
  if (!fileName.includes('.')) return ''
  return fileName.split('.').pop().toLowerCase()
}

/**
 * 根据文件名后缀生成表格中的短类型标识，保持截图中的紧凑文件徽标风格。
 */
const resolveFileType = (fileName) => {
  const suffix = resolveFileExtension(fileName).toUpperCase() || 'FILE'
  const shortMap = {
    DOC: 'W',
    DOCX: 'W',
    PPT: 'P',
    PPTX: 'P',
    XLS: 'XLS',
    XLSX: 'XLS',
    TEXT: 'TXT',
    JPG: 'IMG',
    JPEG: 'IMG',
    PNG: 'IMG',
    WEBP: 'IMG',
    BMP: 'IMG',
    TIF: 'IMG',
    TIFF: 'IMG',
    GIF: 'IMG',
  }
  return shortMap[suffix] || suffix
}

/**
 * 把字节数转换为适合表格展示的文件大小文本。
 */
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
</script>

<style scoped>
.nexus-page {
  height: calc(100vh - 106px);
  min-height: 720px;
  overflow-y: auto;
  overflow-x: hidden;
  background:
    radial-gradient(circle at 64% 8%, rgba(0, 158, 126, 0.12), transparent 28%),
    linear-gradient(180deg, #f8fcfb 0%, #f4f8fb 46%, #f7fafc 100%);
  color: #162033;
}

.nexus-page::-webkit-scrollbar {
  width: 10px;
}

.nexus-page::-webkit-scrollbar-track {
  background: #eaf2f7;
}

.nexus-page::-webkit-scrollbar-thumb {
  border: 2px solid #eaf2f7;
  border-radius: 999px;
  background: #9fb4c8;
}

.nexus-topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 72px;
  padding: 0 clamp(28px, 3.4vw, 64px);
  border-bottom: 1px solid #e4edf3;
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(14px);
}

.brand-lockup,
.user-chip,
.notice-button,
.ghost-button,
.solid-button,
.mini-button,
.drop-zone,
.row-actions button,
.queue-filter button,
.table-footer button {
  border: 0;
  background: transparent;
  color: inherit;
}

.brand-lockup {
  display: inline-flex;
  align-items: center;
  gap: 13px;
  text-align: left;
}

.brand-mark {
  color: #00866d;
  font-size: 33px;
  font-weight: 950;
  line-height: 1;
}

.brand-lockup strong,
.brand-lockup small {
  display: block;
}

.brand-lockup strong {
  color: #0e1f35;
  font-size: 26px;
  font-weight: 900;
  letter-spacing: 0;
}

.brand-lockup small {
  margin-top: 2px;
  color: #8493a5;
  font-size: 12px;
  font-weight: 700;
}

.topbar-actions,
.hero-actions,
.row-actions,
.table-footer,
.graph-legend {
  display: flex;
  align-items: center;
}

.topbar-actions {
  gap: 24px;
}

.notice-button {
  position: relative;
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  color: #2d3a51;
}

.notice-button svg {
  width: 22px;
  height: 22px;
}

.notice-button i {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 8px;
  height: 8px;
  border: 2px solid #ffffff;
  border-radius: 50%;
  background: #ef3527;
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 11px;
  font-weight: 800;
}

.user-chip span {
  display: grid;
  width: 46px;
  height: 46px;
  place-items: center;
  border-radius: 50%;
  background: #dff7eb;
  color: #00866d;
  font-size: 22px;
  font-weight: 950;
}

.user-chip svg {
  width: 15px;
  height: 15px;
}

.nexus-main {
  width: min(100%, 1840px);
  margin: 0 auto;
  padding: 22px clamp(28px, 3.4vw, 64px) 34px;
}

.hero-band {
  position: relative;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  min-height: 128px;
  gap: 24px;
  overflow: hidden;
}

.hero-band::before {
  content: "";
  position: absolute;
  inset: -70px -80px auto 18%;
  height: 170px;
  background:
    radial-gradient(ellipse at center, rgba(0, 156, 128, 0.14), transparent 62%),
    repeating-radial-gradient(ellipse at center, rgba(0, 156, 128, 0.13) 0 1px, transparent 1px 8px);
  opacity: 0.75;
  pointer-events: none;
}

.hero-copy,
.hero-actions {
  position: relative;
  z-index: 1;
}

.hero-eyebrow {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 14px;
  border-radius: 999px;
  background: #dff6e9;
  color: #00795f;
  font-size: 13px;
  font-weight: 900;
}

.hero-copy h1 {
  margin-top: 13px;
  color: #111b35;
  font-size: 34px;
  line-height: 1.12;
  letter-spacing: 0;
}

.hero-copy p {
  margin-top: 12px;
  color: #5f7087;
  font-size: 14px;
  font-weight: 700;
}

.hero-actions {
  gap: 16px;
  padding-top: 28px;
}

.ghost-button,
.solid-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 136px;
  min-height: 46px;
  gap: 8px;
  border-radius: 8px;
  font-size: 15px;
  font-weight: 900;
}

.ghost-button {
  border: 1px solid #d9e4ee;
  background: #ffffff;
  color: #26354d;
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.05);
}

.solid-button {
  border: 1px solid #00866d;
  background: #00866d;
  color: #ffffff;
  box-shadow: 0 12px 22px rgba(0, 134, 109, 0.22);
}

.ghost-button svg,
.solid-button svg {
  width: 18px;
  height: 18px;
}

.hidden-input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 22px;
  margin-top: -4px;
}

.metric-card,
.panel {
  border: 1px solid #dce7ee;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.07);
}

.metric-card {
  display: grid;
  grid-template-columns: 68px minmax(0, 1fr);
  align-items: center;
  min-height: 108px;
  padding: 18px 20px;
  gap: 14px;
}

.metric-icon {
  display: grid;
  width: 58px;
  height: 58px;
  place-items: center;
  border-radius: 50%;
}

.metric-icon svg {
  width: 34px;
  height: 34px;
}

.metric-icon.green,
.metric-icon.mint {
  background: #ddf7ec;
  color: #00866d;
}

.metric-icon.blue {
  background: #e4efff;
  color: #3678ef;
}

.metric-icon.purple {
  background: #f0e7ff;
  color: #7c3aed;
}

.metric-card p,
.metric-card small {
  color: #617188;
  font-weight: 700;
}

.metric-card p {
  font-size: 13px;
}

.metric-card strong {
  display: block;
  margin-top: 5px;
  color: #101b33;
  font-size: 28px;
  line-height: 1;
}

.metric-card small {
  display: block;
  margin-top: 8px;
  font-size: 12px;
}

.workbench-grid {
  display: grid;
  grid-template-columns: minmax(860px, 1.72fr) minmax(420px, 0.96fr);
  gap: 22px;
  align-items: start;
  margin-top: 18px;
}

.panel {
  padding: 16px 20px;
}

.right-stack {
  display: grid;
  gap: 16px;
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.panel-heading.inline {
  align-items: center;
}

.panel-heading h2,
.section-title h3,
.library-block h3 {
  display: flex;
  align-items: center;
  gap: 9px;
  color: #142039;
  font-size: 18px;
  line-height: 1.2;
}

.panel-heading h2 svg {
  width: 20px;
  height: 20px;
  color: #00866d;
}

.panel-heading p {
  margin-top: 5px;
  color: #6c7b90;
  font-size: 13px;
  font-weight: 700;
}

.drop-zone {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  width: 100%;
  min-height: 74px;
  gap: 6px;
  border: 1px dashed #9daec4;
  border-radius: 8px;
  background: #fbfdff;
  color: #142039;
}

.drop-zone svg {
  width: 22px;
  height: 22px;
  color: #00866d;
}

.drop-zone strong {
  font-size: 15px;
  font-weight: 900;
}

.drop-zone span {
  color: #6c7b90;
  font-size: 12px;
  font-weight: 700;
}

.table-block {
  margin-top: 16px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
}

.queue-filter {
  display: flex;
  align-items: center;
  gap: 10px;
}

.queue-filter button {
  min-width: 54px;
  min-height: 28px;
  padding: 0 12px;
  border: 1px solid #dce7ee;
  border-radius: 6px;
  background: #f9fbfd;
  color: #34465f;
  font-size: 12px;
  font-weight: 900;
}

.queue-filter button.active {
  border-color: #b8ead7;
  background: #dff7ec;
  color: #00795f;
}

.data-table {
  overflow: hidden;
  border: 1px solid #dfe8ef;
  border-radius: 8px;
}

.table-row {
  display: grid;
  align-items: center;
  min-height: 39px;
  padding: 0 14px;
  border-top: 1px solid #e8eef4;
  color: #1e2a42;
  font-size: 13px;
  font-weight: 800;
}

.table-row:first-child {
  border-top: 0;
}

.table-head {
  min-height: 32px;
  background: #f8fafc;
  color: #68788f;
  font-size: 12px;
}

.queue-table .table-row {
  grid-template-columns: minmax(220px, 1.55fr) 100px 132px 154px minmax(170px, 1fr) 86px;
}

.library-table .table-row {
  grid-template-columns: minmax(250px, 1.7fr) 88px 134px 108px 122px 122px 112px;
}

.file-cell,
.state-cell {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  gap: 10px;
}

.file-cell strong {
  overflow: hidden;
  min-width: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-copy {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.file-copy small {
  overflow: hidden;
  color: #dc2626;
  font-size: 11px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-type {
  display: inline-grid;
  min-width: 18px;
  height: 18px;
  place-items: center;
  border-radius: 3px;
  background: #ef4444;
  color: #ffffff;
  font-size: 8px;
  font-style: normal;
  font-weight: 950;
}

.file-type.w {
  background: #2f7de1;
}

.file-type.p {
  background: #f05a28;
}

.file-type.zip,
.file-type.txt,
.file-type.xls {
  background: #16a67d;
}

.file-type.img {
  background: #8b5cf6;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0 8px;
  border: 1px solid currentColor;
  border-radius: 5px;
  font-size: 12px;
  font-style: normal;
  font-weight: 900;
}

.status-pill.blue {
  background: #eaf3ff;
  color: #2f7de1;
}

.status-pill.green {
  background: #dcf8ec;
  color: #00866d;
}

.status-pill.purple {
  background: #f1e8ff;
  color: #7c3aed;
}

.status-pill.orange {
  background: #fff5df;
  color: #d97706;
}

.status-pill.red {
  background: #fff1f2;
  color: #dc2626;
}

.status-pill.amber {
  background: #fff7ed;
  color: #b45309;
}

.queue-failed {
  background: #fff7f7;
}

.queue-interrupted {
  background: #fffbeb;
}

.progress-cell {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
}

.progress-cell i {
  display: block;
  height: 5px;
  overflow: hidden;
  border-radius: 999px;
  background: #e4ebf1;
}

.progress-cell b {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #00866d;
}

.progress-cell.failed b {
  background: #dc2626;
}

.progress-cell.interrupted b {
  background: #d97706;
}

.row-actions {
  gap: 14px;
}

.row-actions button {
  display: inline-grid;
  width: 18px;
  height: 22px;
  place-items: center;
  color: #24324c;
}

.row-actions .text-action {
  width: auto;
  min-width: 54px;
  height: 26px;
  padding: 0 8px;
  border-radius: 5px;
  font-size: 12px;
  font-weight: 900;
}

.row-actions .text-action.primary {
  background: #e8f5ff;
  color: #0f67b1;
}

.row-actions .text-action.danger {
  background: #fff1f2;
  color: #dc2626;
}

.row-actions svg {
  width: 16px;
  height: 16px;
}

.state-cell {
  color: #00866d;
  font-size: 12px;
  white-space: nowrap;
}

.state-cell svg {
  width: 15px;
  height: 15px;
}

.state-cell.running {
  color: #2f7de1;
}

.state-cell.waiting {
  color: #d97706;
}

.table-footer {
  justify-content: flex-end;
  gap: 10px;
  margin-top: 14px;
  color: #40516a;
  font-size: 13px;
  font-weight: 800;
}

.table-footer button,
.table-footer select {
  min-width: 34px;
  height: 30px;
  border: 1px solid #dce7ee;
  border-radius: 5px;
  background: #ffffff;
  color: #40516a;
  font-weight: 900;
}

.table-footer .page-current {
  border-color: #00866d;
  background: #00866d;
  color: #ffffff;
}

.table-footer select {
  min-width: 110px;
  padding: 0 10px;
}

.graph-panel {
  min-height: 258px;
}

.mini-button {
  min-height: 30px;
  padding: 0 13px;
  border: 1px solid #dce7ee;
  border-radius: 5px;
  background: #ffffff;
  color: #22324b;
  font-size: 12px;
  font-weight: 900;
}

.graph-content {
  display: grid;
  grid-template-columns: 130px minmax(0, 1fr);
  align-items: center;
  gap: 14px;
}

.graph-stats {
  overflow: hidden;
  border: 1px solid #e1e9f0;
  border-radius: 8px;
}

.graph-stats div {
  min-height: 58px;
  padding: 10px 16px;
  border-top: 1px solid #e1e9f0;
  background: #fbfdff;
}

.graph-stats div:first-child {
  border-top: 0;
}

.graph-stats small {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #63738a;
  font-size: 12px;
  font-weight: 900;
}

.graph-stats small svg {
  width: 13px;
  height: 13px;
  color: #168dcb;
}

.graph-stats strong {
  display: block;
  margin-top: 5px;
  color: #10203a;
  font-size: 18px;
}

.knowledge-graph {
  width: 100%;
  height: 190px;
}

.graph-lines line {
  stroke: #83b4ae;
  stroke-width: 1.5;
}

.graph-node text {
  fill: #53637a;
  font-size: 12px;
  font-weight: 800;
}

.graph-node:first-of-type text {
  fill: #ffffff;
}

.graph-legend {
  justify-content: center;
  gap: 30px;
  color: #516279;
  font-size: 12px;
  font-weight: 800;
}

.graph-legend i {
  display: inline-block;
  width: 9px;
  height: 9px;
  margin-right: 7px;
  border-radius: 50%;
}

.legend-topic {
  background: #3b82f6;
}

.legend-entity {
  background: #8b5cf6;
}

.legend-edge {
  width: 18px !important;
  height: 1px !important;
  border-radius: 0 !important;
  background: #83b4ae;
  vertical-align: middle;
}

.pipeline-line {
  position: relative;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 18px;
  padding: 18px 4px 12px;
}

.pipeline-line::before {
  content: "";
  position: absolute;
  top: 28px;
  left: 28px;
  right: 28px;
  height: 2px;
  background: #00866d;
}

.pipeline-line article {
  position: relative;
  z-index: 1;
  display: grid;
  justify-items: center;
  gap: 5px;
  text-align: center;
}

.pipeline-line span {
  display: grid;
  width: 24px;
  height: 24px;
  place-items: center;
  border-radius: 50%;
  background: #00866d;
  color: #ffffff;
}

.pipeline-line svg {
  width: 14px;
  height: 14px;
}

.pipeline-line strong {
  margin-top: 4px;
  color: #142039;
  font-size: 13px;
}

.pipeline-line small {
  color: #6c7b90;
  font-size: 12px;
  font-weight: 800;
}

.pipeline-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px solid #e8eef4;
}

.pipeline-footer span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #00866d;
  font-size: 12px;
  font-weight: 900;
}

.pipeline-footer svg {
  width: 15px;
  height: 15px;
}

.recent-list {
  display: grid;
  gap: 4px;
}

.recent-list article {
  display: grid;
  grid-template-columns: minmax(170px, 1fr) 100px minmax(168px, 1fr);
  align-items: center;
  min-height: 50px;
  gap: 12px;
  border-bottom: 1px solid #e8eef4;
  color: #1e2a42;
  font-size: 13px;
  font-weight: 800;
}

.recent-list article:last-child {
  border-bottom: 0;
}

.recent-list time {
  color: #617188;
  font-size: 12px;
}

.recent-list em {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: #45566e;
  font-size: 12px;
  font-style: normal;
}

.recent-list em svg {
  width: 14px;
  height: 14px;
  color: #00866d;
}

@media (max-width: 1500px) {
  .nexus-main {
    padding-inline: 34px;
  }

  .workbench-grid {
    grid-template-columns: minmax(760px, 1.55fr) minmax(390px, 0.95fr);
  }

  .queue-table .table-row {
    grid-template-columns: minmax(210px, 1.5fr) 86px 112px 142px minmax(144px, 1fr) 72px;
  }

  .library-table .table-row {
    grid-template-columns: minmax(220px, 1.6fr) 64px 112px 98px 108px 108px 94px;
  }
}

@media (max-width: 1180px) {
  .metric-grid,
  .workbench-grid {
    grid-template-columns: 1fr;
  }

  .workbench-grid {
    gap: 16px;
  }
}

@media (max-width: 760px) {
  .nexus-topbar,
  .hero-band,
  .section-title,
  .pipeline-footer {
    flex-direction: column;
    align-items: flex-start;
  }

  .nexus-topbar {
    position: relative;
    height: auto;
    gap: 12px;
    padding-block: 14px;
  }

  .brand-lockup strong {
    font-size: 20px;
  }

  .nexus-main {
    padding: 18px;
  }

  .hero-actions,
  .queue-filter {
    flex-wrap: wrap;
  }

  .metric-grid,
  .graph-content,
  .pipeline-line {
    grid-template-columns: 1fr;
  }

  .data-table {
    overflow-x: auto;
  }

  .queue-table .table-row,
  .library-table .table-row {
    min-width: 920px;
  }
}
</style>
