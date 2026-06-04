<template>
  <StudioLayout>
    <main class="library-page">
      <section class="library-hero">
        <div>
          <span class="hero-eyebrow">Document Library</span>
          <h1>文档库</h1>
          <p>集中管理真实上传的企业资料，上传后先保留原文件，用户手动点击解析后再进入知识处理队列。</p>
        </div>
        <div class="hero-actions">
          <button class="outline-button" type="button" @click="loadFileList">
            <Refresh />
            刷新
          </button>
          <button class="primary-button" type="button" @click="openFilePicker">
            <UploadFilled />
            上传文档
          </button>
        </div>
        <input
          ref="fileInputRef"
          class="hidden-input"
          type="file"
          multiple
          accept=".pdf,.txt,.doc,.docx,.ppt,.pptx,.wps,.wpt,.dps,.dpt,.wpd"
          @change="handleFileChange"
        />
      </section>

      <section class="library-toolbar">
        <div class="stat-pill">
          <strong>{{ libraryTotal }}</strong>
          <span>文档总数</span>
        </div>
        <div class="stat-pill">
          <strong>{{ parsingCount }}</strong>
          <span>解析中</span>
        </div>
        <div class="stat-pill">
          <strong>{{ failedCount }}</strong>
          <span>解析失败</span>
        </div>
        <button class="text-button" type="button" @click="openFilePicker">
          <Upload />
          继续上传
        </button>
      </section>

      <section v-loading="loading" class="document-grid" aria-label="已上传文档">
        <article
          v-for="file in libraryFiles"
          :key="file.fileId"
          class="document-card"
          tabindex="0"
          @click="openEditor(file)"
          @keyup.enter="openEditor(file)"
        >
          <div class="document-cover" :class="file.coverTone">
            <component :is="file.icon" />
            <span>{{ file.typeLabel }}</span>
          </div>
          <div class="document-meta">
            <div class="document-title-row">
              <h2 :title="file.name">{{ file.name }}</h2>
              <el-dropdown trigger="click" @click.stop>
                <button class="more-button" type="button" title="更多操作" @click.stop>
                  <MoreFilled />
                </button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item @click="downloadFile(file)">
                      <Download />
                      下载到本地
                    </el-dropdown-item>
                    <el-dropdown-item v-if="canSubmitParse(file)" @click="parseFile(file)">
                      <Refresh />
                      解析
                    </el-dropdown-item>
                    <el-dropdown-item v-else-if="canRetryParse(file)" @click="parseFile(file)">
                      <Refresh />
                      重新解析
                    </el-dropdown-item>
                    <el-dropdown-item v-else-if="isRetryExhausted(file)" @click="showParseAlarm">
                      <WarningFilled />
                      解析报警
                    </el-dropdown-item>
                    <el-dropdown-item class="danger-item" @click="deleteFile(file)">
                      <Delete />
                      删除文档
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
            <p>{{ file.time }}</p>
            <div class="badge-row">
              <span class="upload-badge">{{ file.uploadText }}</span>
              <span v-if="file.showParseBadge" class="parse-badge" :class="file.parseTone">{{ file.parseText }}</span>
            </div>
          </div>
        </article>

        <div v-if="!loading && !libraryFiles.length" class="empty-library">
          <Files />
          <strong>暂无文档</strong>
          <p>上传 PDF、TXT、Word、PPT 或 WPS 文档后，会在这里展示。</p>
          <button class="primary-button" type="button" @click="openFilePicker">上传第一份文档</button>
        </div>
      </section>

      <el-dialog
        v-model="uploadDialogVisible"
        title="上传列表"
        width="720px"
        :close-on-click-modal="false"
        :before-close="handleUploadDialogClose"
      >
        <div class="upload-dialog-body">
          <div class="upload-rules">
            <strong>文件限制</strong>
            <span>单个文件最大 200MB；小于 5MB 直接上传，5MB 起按至少 5MB 分片上传并支持断点续传；暂不支持图片、视频、Excel 和外链图床。</span>
          </div>
          <div class="upload-list">
            <article v-for="item in uploadItems" :key="item.id" class="upload-item" :class="item.status">
              <div class="upload-file-icon" :class="item.coverTone">
                <component :is="item.icon" />
              </div>
              <div class="upload-copy">
                <strong :title="item.name">{{ item.name }}</strong>
                <span>{{ item.sizeText }} · {{ item.statusText }}</span>
                <em v-if="item.errorMessage">{{ item.errorMessage }}</em>
                <div class="upload-progress">
                  <i><b :style="{ width: `${item.progress}%` }"></b></i>
                  <small>{{ item.progress }}%</small>
                </div>
                <div v-if="item.status === 'failed'" class="upload-item-actions">
                  <button class="mini-action-button" type="button" @click="retryUploadItem(item)">
                    <Refresh />
                    重新上传
                  </button>
                  <button class="mini-action-button danger" type="button" @click="discardUploadItem(item)">
                    <Delete />
                    移除
                  </button>
                </div>
              </div>
            </article>
          </div>
        </div>
        <template #footer>
          <button class="outline-button" type="button" @click="handleUploadDialogClose(() => {})">关闭</button>
          <button class="primary-button" type="button" @click="handleContinueAdd">继续添加</button>
        </template>
      </el-dialog>
    </main>
  </StudioLayout>
</template>

<script setup>
import { computed, markRaw, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Delete,
  Document,
  Download,
  Files,
  MoreFilled,
  Notebook,
  Refresh,
  Tickets,
  Upload,
  UploadFilled,
  WarningFilled,
} from '@element-plus/icons-vue'
import StudioLayout from '../components/StudioLayout.vue'
import { fileApi } from '../api/file'
import { notifyUserOperationChanged } from '../utils/sidebarStats'

const router = useRouter()
const fileInputRef = ref(null)
const loading = ref(false)
const libraryFiles = ref([])
const libraryTotal = ref(0)
const uploadDialogVisible = ref(false)
const uploadItems = ref([])
const uploading = ref(false)
const currentUpload = ref(null)
const cancelUploadQueueRequested = ref(false)

const maxFileSize = 200 * 1024 * 1024
const minChunkSize = 5 * 1024 * 1024
const supportedExtensions = new Set(['pdf', 'txt', 'doc', 'docx', 'ppt', 'pptx', 'wps', 'wpt', 'dps', 'dpt', 'wpd'])

const parsingCount = computed(() => libraryFiles.value.filter((file) => ['PROCESSING', 'PENDING'].includes(file.parseStatus)).length)
const failedCount = computed(() => libraryFiles.value.filter((file) => file.parseStatus === 'FAILED').length)

onMounted(() => {
  loadFileList()
})

/**
 * 加载当前用户已上传文档列表。
 */
const loadFileList = async () => {
  loading.value = true
  try {
    const response = await fileApi.getFileList({ pageNum: 1, pageSize: 50, knowledgeBaseId: 'default' })
    const page = response.data || {}
    libraryFiles.value = (page.records || [])
      .filter((file) => file.uploadStatus === 'UPLOADED')
      .map(normalizeFile)
    libraryTotal.value = Number(page.total || libraryFiles.value.length)
  } catch (error) {
    ElMessage.error(error?.message || '文档列表加载失败')
  } finally {
    loading.value = false
  }
}

/**
 * 打开文件选择器。
 */
const openFilePicker = () => {
  fileInputRef.value?.click()
}

/**
 * 处理文件选择并加入排队上传列表。
 */
const handleFileChange = async (event) => {
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  if (!files.length) return
  uploadDialogVisible.value = true
  const recoverableMap = await loadRecoverableUploadMap()
  const rows = files.map((file) => createUploadItem(file, recoverableMap.get(uploadFingerprint(file))))
  uploadItems.value = [...uploadItems.value, ...rows]
  processUploadQueue()
}

/**
 * 使用前端互斥锁顺序处理上传队列。
 */
const processUploadQueue = async () => {
  if (uploading.value) return
  uploading.value = true
  cancelUploadQueueRequested.value = false
  try {
    for (const item of uploadItems.value) {
      if (cancelUploadQueueRequested.value) break
      if (item.status !== 'waiting') continue
      const reason = validateFile(item.file)
      if (reason) {
        item.status = 'failed'
        item.statusText = '校验失败'
        item.errorMessage = reason
        continue
      }
      await uploadOne(item)
    }
  } finally {
    uploading.value = false
    currentUpload.value = null
    await loadFileList()
  }
}

/**
 * 上传单个文件并持续回显进度。
 */
const uploadOne = async (item) => {
  const controller = new AbortController()
  currentUpload.value = { item, controller }
  item.status = 'uploading'
  item.statusText = '上传中'
  item.progress = 0
  notifyUserOperationChanged()

  try {
    const response = await fileApi.upload(item.file, {
      knowledgeBaseId: 'default',
      signal: controller.signal,
      uploadId: item.uploadId,
      chunkSize: item.chunkSize,
      uploadedChunks: item.uploadedChunkIndexes,
      onSession: ({ uploadId, chunkSize, totalChunks }) => {
        item.uploadId = uploadId
        item.chunkSize = chunkSize || item.chunkSize
        item.totalChunks = totalChunks || item.totalChunks
      },
      onProgress: ({ percent, mode, uploadedChunks, totalChunks }) => {
        item.progress = percent
        item.uploadedChunks = uploadedChunks ?? item.uploadedChunks
        item.totalChunks = totalChunks || item.totalChunks
        item.statusText = mode === 'merge' ? '合并分片中' : '上传中'
      }
    })
    item.uploadId = response.data?.uploadId || item.uploadId
    item.status = 'success'
    item.statusText = '上传成功'
    item.progress = 100
    ElMessage.success(`${item.name} 上传成功`)
  } catch (error) {
    item.status = controller.signal.aborted ? 'canceled' : 'failed'
    item.statusText = controller.signal.aborted ? '已取消' : '上传失败'
    item.errorMessage = controller.signal.aborted ? '用户取消上传' : (error?.message || '上传失败')
    if (controller.signal.aborted && item.uploadId) {
      fileApi.cancelUpload(item.uploadId).catch(() => {})
    }
  }
}

/**
 * 继续添加文件前清理失败上传项，保证 Redis 临时缓存和页面列表同步消失。
 */
const handleContinueAdd = async () => {
  await clearFailedUploadItems()
  openFilePicker()
}

/**
 * 收集当前上传列表中的失败会话 ID。
 */
const failedUploadIds = () => uploadItems.value
  .filter((item) => item.status === 'failed' && item.uploadId)
  .map((item) => item.uploadId)

/**
 * 清理失败上传项，后端会同步取消会话、删除 Redis 临时项和 MinIO 临时分片。
 */
const clearFailedUploadItems = async () => {
  const uploadIds = failedUploadIds()
  if (uploadIds.length) {
    try {
      await fileApi.discardFailedUploads(uploadIds)
    } catch (error) {
      ElMessage.warning(error?.message || '清理失败上传缓存失败，请稍后重试')
      return
    }
  }
  uploadItems.value = uploadItems.value.filter((item) => item.status !== 'failed')
}

/**
 * 重新上传失败文件；如果后端仍保留分片会话，则优先断点续传或重新 complete。
 */
const retryUploadItem = async (item) => {
  if (uploading.value) {
    ElMessage.warning('上传队列正在处理，请稍后再重试')
    return
  }
  await refreshRetrySession(item)
  item.status = 'waiting'
  item.statusText = item.uploadId ? '准备断点续传' : '排队中'
  item.errorMessage = ''
  item.progress = item.totalChunks > 0 ? Math.min(99, Math.round((item.uploadedChunks / item.totalChunks) * 100)) : 0
  processUploadQueue()
}

/**
 * 移除单个失败上传项，并清理后端 Redis 临时状态。
 */
const discardUploadItem = async (item) => {
  if (item.uploadId) {
    try {
      await fileApi.discardFailedUploads([item.uploadId])
    } catch (error) {
      ElMessage.warning(error?.message || '清理失败上传缓存失败，请稍后重试')
      return
    }
  }
  uploadItems.value = uploadItems.value.filter((row) => row.id !== item.id)
}

/**
 * 刷新失败会话的断点信息；旧会话被清理时自动改为全新上传。
 */
const refreshRetrySession = async (item) => {
  if (!item.uploadId) return
  try {
    const response = await fileApi.getChunkStatus(item.uploadId)
    const status = response.data?.status
    if (['CANCELED', 'EXPIRED'].includes(status)) {
      resetUploadSession(item)
      return
    }
    item.chunkSize = Number(response.data?.chunkSize || item.chunkSize || 0)
    item.totalChunks = Number(response.data?.totalChunks || item.totalChunks || 0)
    item.uploadedChunks = Number(response.data?.uploadedChunks || 0)
    item.uploadedChunkIndexes = response.data?.uploadedChunkIndexes || []
    if (item.chunkSize < minChunkSize && item.uploadedChunks < item.totalChunks) {
      await fileApi.discardFailedUploads([item.uploadId]).catch(() => {})
      resetUploadSession(item)
    }
  } catch {
    resetUploadSession(item)
  }
}

/**
 * 清空旧上传会话，让失败文件从第一片开始重新上传。
 */
const resetUploadSession = (item) => {
  item.uploadId = ''
  item.chunkSize = 0
  item.totalChunks = 0
  item.uploadedChunks = 0
  item.uploadedChunkIndexes = []
}

/**
 * 关闭上传弹窗，上传中时提示并取消当前文件。
 */
const handleUploadDialogClose = async (done) => {
  if (currentUpload.value?.item?.status === 'uploading') {
    try {
      await ElMessageBox.confirm('正在上传中，退出将会取消上传，确定要退出么', '取消上传', {
        confirmButtonText: '确定',
        cancelButtonText: '继续上传',
        type: 'warning',
      })
      cancelUploadQueueRequested.value = true
      currentUpload.value.controller.abort()
      uploadItems.value = uploadItems.value.filter((item) => item.status === 'success')
      uploadDialogVisible.value = false
      done?.()
    } catch {
      return
    }
    return
  }
  uploadDialogVisible.value = false
  done?.()
}

/**
 * 打开文档编辑页。
 */
const openEditor = (file) => {
  if (!file.fileId) return
  router.push(`/files/${file.fileId}/editor`)
}

/**
 * 下载当前 MinIO 版本。
 */
const downloadFile = async (file) => {
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
 * 删除文档。
 */
const deleteFile = async (file) => {
  await ElMessageBox.confirm(`确定删除「${file.name}」吗？`, '删除文档', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  })
  notifyUserOperationChanged()
  await fileApi.delete(file.fileId)
  ElMessage.success('删除成功')
  await loadFileList()
}

/**
 * 手动提交解析或重新解析请求。
 */
const parseFile = async (file) => {
  if (isRetryExhausted(file)) {
    await showParseAlarm()
    return
  }
  if (file.parseStatus === 'FAILED') {
    await ElMessageBox.confirm(`解析失败，是否重新解析「${file.name}」？`, '重新解析', {
      confirmButtonText: '重新解析',
      cancelButtonText: '取消',
      type: 'warning',
    })
  }
  notifyUserOperationChanged()
  await fileApi.reindex(file.fileId)
  ElMessage.success(file.parseStatus === 'FAILED' ? '已提交重新解析' : '已提交解析')
  await loadFileList()
}

/**
 * 判断文件是否可以首次提交解析。
 */
const canSubmitParse = (file) => file.parseStatus === 'NOT_REQUESTED'

/**
 * 判断文件是否可以重新解析。
 */
const canRetryParse = (file) => file.parseStatus === 'FAILED' && Number(file.parseRetryCount || 0) < 1

/**
 * 判断文件是否已经用尽重新解析机会。
 */
const isRetryExhausted = (file) => file.parseStatus === 'FAILED' && Number(file.parseRetryCount || 0) >= 1

/**
 * 展示解析重试耗尽告警。
 */
const showParseAlarm = async () => {
  await ElMessageBox.alert('请稍后再试', '解析报警', {
    confirmButtonText: '知道了',
    type: 'warning',
  })
}

/**
 * 创建上传队列项。
 */
const createUploadItem = (file, recoverable = null) => {
  const meta = resolveTypeMeta(file.name)
  const uploadedChunks = recoverable?.uploadedChunks || 0
  const totalChunks = recoverable?.totalChunks || 0
  return {
    id: `upload_${Date.now()}_${Math.random().toString(16).slice(2)}`,
    file,
    name: file.name,
    sizeText: formatFileSize(file.size),
    status: 'waiting',
    statusText: recoverable?.uploadId ? '已找到断点，等待续传' : '排队中',
    progress: totalChunks > 0 ? Math.min(99, Math.round((uploadedChunks / totalChunks) * 100)) : 0,
    uploadId: recoverable?.uploadId || '',
    chunkSize: recoverable?.chunkSize || 0,
    uploadedChunks,
    totalChunks,
    uploadedChunkIndexes: recoverable?.uploadedChunkIndexes || [],
    errorMessage: '',
    ...meta,
  }
}

/**
 * 查询后端可恢复上传会话，并按文件名和大小建立匹配索引。
 */
const loadRecoverableUploadMap = async () => {
  try {
    const response = await fileApi.getRecoverableUploads()
    return new Map((response.data || [])
      .filter(canReuseRecoverableUpload)
      .map((item) => [`${item.fileName}::${item.fileSize}`, item]))
  } catch {
    return new Map()
  }
}

/**
 * 判断后端断点会话是否可复用；旧小分片全量上传完成时仍允许进入 complete 兜底。
 */
const canReuseRecoverableUpload = (item) => {
  const chunkSize = Number(item.chunkSize || 0)
  const uploadedChunks = Number(item.uploadedChunks || 0)
  const totalChunks = Number(item.totalChunks || 0)
  return chunkSize >= minChunkSize || (totalChunks > 0 && uploadedChunks >= totalChunks)
}

/**
 * 生成本地文件与后端上传会话的匹配指纹。
 */
const uploadFingerprint = (file) => `${file.name}::${file.size}`

/**
 * 校验文件格式和大小。
 */
const validateFile = (file) => {
  const extension = resolveExtension(file.name)
  if (!supportedExtensions.has(extension)) return '仅支持 PDF、TXT、Word、PPT、WPS/WPD 文件，不支持图片、视频、Excel 或外链图床'
  if (file.size <= 0) return '不能上传空文件'
  if (file.size > maxFileSize) return '单个文件不能超过 200MB'
  return ''
}

/**
 * 规范化后端文件展示字段。
 */
const normalizeFile = (file) => {
  const meta = resolveTypeMeta(file.name || file.originalName || '')
  return {
    fileId: file.fileId || file.id,
    name: file.name || file.originalName || '未命名文档',
    time: file.timeText || formatDate(file.createdAt),
    uploadStatus: file.uploadStatus || 'UPLOADED',
    uploadText: file.statusText || '已上传',
    parseStatus: normalizeParseStatus(file.parseStatus),
    parseRetryCount: Number(file.parseRetryCount || 0),
    showParseBadge: shouldShowParseBadge(file.parseStatus),
    parseText: resolveParseText(file.parseStatus),
    parseTone: resolveParseTone(file.parseStatus),
    ...meta,
  }
}

/**
 * 解析不同文件格式的图标和色调。
 */
const resolveTypeMeta = (fileName) => {
  const ext = resolveExtension(fileName)
  if (ext === 'pdf') return { icon: markRaw(Document), typeLabel: 'PDF', coverTone: 'pdf' }
  if (ext === 'txt') return { icon: markRaw(Tickets), typeLabel: 'TXT', coverTone: 'txt' }
  if (['ppt', 'pptx', 'dps', 'dpt'].includes(ext)) return { icon: markRaw(Notebook), typeLabel: 'PPT', coverTone: 'ppt' }
  if (['wps', 'wpt', 'wpd'].includes(ext)) return { icon: markRaw(Document), typeLabel: 'WPS', coverTone: 'wps' }
  return { icon: markRaw(Document), typeLabel: 'WORD', coverTone: 'word' }
}

/**
 * 获取文件扩展名。
 */
const resolveExtension = (fileName) => {
  if (!fileName || !fileName.includes('.')) return ''
  return fileName.split('.').pop().toLowerCase()
}

/**
 * 规范化解析状态，空值表示未发起解析。
 */
const normalizeParseStatus = (status) => status || 'NOT_REQUESTED'

/**
 * 判断是否展示第二个解析状态标签。
 */
const shouldShowParseBadge = (status) => normalizeParseStatus(status) !== 'NOT_REQUESTED'

/**
 * 解析状态文案。
 */
const resolveParseText = (status) => {
  if (status === 'PENDING') return '待解析'
  if (status === 'PROCESSING') return '解析中'
  if (status === 'SUCCESS') return '已解析'
  if (status === 'FAILED') return '解析失败'
  return ''
}

/**
 * 解析状态色调。
 */
const resolveParseTone = (status) => {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'failed'
  if (status === 'PENDING') return 'waiting'
  if (status === 'PROCESSING') return 'running'
  return 'idle'
}

/**
 * 格式化文件大小。
 */
const formatFileSize = (size) => {
  if (!size) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let value = size
  let index = 0
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024
    index += 1
  }
  return `${value >= 10 || index === 0 ? value.toFixed(0) : value.toFixed(1)} ${units[index]}`
}

/**
 * 格式化上传日期。
 */
const formatDate = (value) => {
  if (!value) return '刚刚'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '刚刚'
  return `${date.getFullYear()}/${date.getMonth() + 1}/${date.getDate()}`
}
</script>

<style scoped>
.library-page {
  min-height: calc(100vh - 72px);
  padding: 26px 32px 42px;
  background:
    linear-gradient(135deg, rgba(0, 141, 114, 0.10), rgba(255, 255, 255, 0.72) 38%),
    #f5faf7;
}

.library-hero,
.library-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  max-width: 1400px;
  margin: 0 auto;
}

.library-hero {
  padding: 22px 0 18px;
}

.hero-eyebrow {
  display: block;
  color: #008d72;
  font-weight: 700;
  font-size: 13px;
  margin-bottom: 8px;
}

.library-hero h1 {
  margin: 0;
  font-size: 34px;
  color: #12352e;
  letter-spacing: 0;
}

.library-hero p {
  margin: 10px 0 0;
  color: #5c6f69;
  font-size: 15px;
}

.hero-actions,
.library-toolbar {
  display: flex;
  align-items: center;
}

.hero-actions {
  gap: 10px;
}

.primary-button,
.outline-button,
.text-button {
  height: 38px;
  border-radius: 8px;
  border: 1px solid transparent;
  padding: 0 14px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-weight: 700;
  cursor: pointer;
}

.primary-button {
  background: #008d72;
  color: #fff;
  box-shadow: 0 8px 20px rgba(0, 141, 114, 0.22);
}

.outline-button {
  background: #fff;
  color: #006b58;
  border-color: #cfe7df;
}

.text-button {
  background: transparent;
  color: #008d72;
}

.primary-button svg,
.outline-button svg,
.text-button svg {
  width: 16px;
  height: 16px;
}

.hidden-input {
  display: none;
}

.library-toolbar {
  margin-top: 8px;
  padding: 14px 18px;
  border: 1px solid #dcece7;
  background: rgba(255, 255, 255, 0.82);
  border-radius: 8px;
}

.stat-pill {
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
  color: #5f756f;
}

.stat-pill strong {
  color: #0b6f5d;
  font-size: 22px;
}

.document-grid {
  max-width: 1400px;
  margin: 22px auto 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 22px;
}

.document-card {
  overflow: hidden;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #dfe8e4;
  box-shadow: 0 16px 36px rgba(33, 73, 64, 0.08);
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.document-card:hover,
.document-card:focus {
  transform: translateY(-2px);
  border-color: #9bd2c4;
  box-shadow: 0 20px 42px rgba(0, 141, 114, 0.15);
  outline: none;
}

.document-cover {
  height: 148px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #fff;
}

.document-cover svg {
  width: 48px;
  height: 48px;
}

.document-cover span {
  font-weight: 800;
  letter-spacing: 0;
}

.document-cover.pdf { background: linear-gradient(135deg, #0f9d7f, #91d7c1); }
.document-cover.word { background: linear-gradient(135deg, #1a8f70, #b6dfce); }
.document-cover.ppt { background: linear-gradient(135deg, #5fa75c, #dbd79a); }
.document-cover.txt { background: linear-gradient(135deg, #177c67, #9dcfbd); }
.document-cover.wps { background: linear-gradient(135deg, #267867, #d5ca7f); }

.document-meta {
  min-height: 112px;
  padding: 16px 18px 18px;
  background: #fff;
}

.document-title-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 32px;
  align-items: start;
  gap: 8px;
}

.document-title-row h2 {
  margin: 0;
  font-size: 17px;
  line-height: 1.35;
  color: #10251f;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.more-button {
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #8a9692;
  cursor: pointer;
}

.more-button svg {
  width: 18px;
  height: 18px;
}

.document-meta p {
  margin: 9px 0 10px;
  color: #687a75;
  font-size: 14px;
}

.badge-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.upload-badge,
.parse-badge {
  display: inline-flex;
  align-items: center;
  height: 24px;
  border-radius: 999px;
  padding: 0 10px;
  font-size: 13px;
  font-weight: 700;
}

.upload-badge {
  background: #e9f8f1;
  color: #0a7c62;
}

.parse-badge.waiting {
  background: #fff8e1;
  color: #a56900;
}

.parse-badge.running {
  background: #e9f5ff;
  color: #2b6cb0;
}

.parse-badge.success {
  background: #e8f7df;
  color: #3f9a23;
}

.parse-badge.failed {
  background: #fff1f0;
  color: #d24a43;
}

.danger-item {
  color: #d24a43;
}

.empty-library {
  grid-column: 1 / -1;
  min-height: 320px;
  border: 1px dashed #b7d8ce;
  background: rgba(255, 255, 255, 0.7);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #5d746e;
}

.empty-library svg {
  width: 52px;
  height: 52px;
  color: #008d72;
}

.empty-library strong {
  color: #143f35;
  font-size: 20px;
}

.empty-library p {
  margin: 0 0 8px;
}

.upload-dialog-body {
  display: grid;
  gap: 16px;
}

.upload-rules {
  display: grid;
  gap: 6px;
  padding: 14px;
  border-radius: 8px;
  background: #f0faf6;
  color: #557069;
}

.upload-rules strong {
  color: #0a6d5a;
}

.upload-list {
  display: grid;
  gap: 10px;
  max-height: 420px;
  overflow: auto;
}

.upload-item {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr);
  gap: 12px;
  padding: 12px;
  border: 1px solid #e0ebe7;
  border-radius: 8px;
  background: #fff;
}

.upload-file-icon {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.upload-file-icon.pdf,
.upload-file-icon.word,
.upload-file-icon.ppt,
.upload-file-icon.txt,
.upload-file-icon.wps {
  background: #008d72;
}

.upload-copy {
  min-width: 0;
  display: grid;
  gap: 5px;
}

.upload-copy strong {
  color: #17342d;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.upload-copy span,
.upload-copy em {
  color: #657872;
  font-style: normal;
  font-size: 13px;
}

.upload-copy em {
  color: #d24a43;
}

.upload-progress {
  display: grid;
  grid-template-columns: 1fr 42px;
  gap: 8px;
  align-items: center;
}

.upload-progress i {
  height: 7px;
  border-radius: 999px;
  background: #e4eee9;
  overflow: hidden;
}

.upload-progress b {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #008d72;
}

.upload-progress small {
  color: #557069;
  text-align: right;
}

.upload-item-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 2px;
}

.mini-action-button {
  height: 28px;
  border-radius: 8px;
  border: 1px solid #cfe7df;
  background: #f7fcfa;
  color: #00765f;
  padding: 0 10px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.mini-action-button svg {
  width: 14px;
  height: 14px;
}

.mini-action-button.danger {
  border-color: #f3cbc8;
  background: #fff7f6;
  color: #c8443c;
}
</style>
