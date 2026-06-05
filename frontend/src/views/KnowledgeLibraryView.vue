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
              <h2 :title="file.name">{{ file.displayName }}</h2>
              <el-dropdown trigger="click" @click.stop>
                <button class="more-button" type="button" title="更多操作" @click.stop>
                  <MoreFilled />
                </button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item class="file-menu-item download-menu-item" @click="downloadFile(file)">
                      <Download />
                      <span>下载到本地</span>
                    </el-dropdown-item>
                    <el-dropdown-item v-if="canSubmitParse(file)" class="file-menu-item parse-menu-item" @click.stop="parseFile(file)">
                      <Refresh />
                      <span>解析文档</span>
                    </el-dropdown-item>
                    <el-dropdown-item v-else-if="canRetryParse(file)" class="file-menu-item parse-menu-item" @click.stop="parseFile(file)">
                      <Refresh />
                      <span>重新解析</span>
                    </el-dropdown-item>
                    <el-dropdown-item v-else-if="isRetryExhausted(file)" @click="showParseAlarm">
                      <WarningFilled />
                      解析报警
                    </el-dropdown-item>
                    <el-dropdown-item class="file-menu-item danger-item" @click="deleteFile(file)">
                      <Delete />
                      <span>删除文档</span>
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
        title="上传队列与文档信息"
        width="980px"
        :close-on-click-modal="false"
        :before-close="handleUploadDialogClose"
      >
        <div class="upload-dialog-body">
          <div class="upload-rules">
            <strong>文件限制</strong>
            <span>单个文件最大 200MB；小于 5MB 直接上传，5MB 起按至少 5MB 分片上传并支持断点续传。文档信息可以先不填，系统会按默认个人资料库保存，后续解析时再补全。</span>
          </div>
          <div v-if="uploadItems.length" class="upload-batch-actions">
            <button class="mini-action-button" type="button" @click="applyCurrentCategoryToLater(activeUploadItem, activeUploadIndex)">
              套用当前分类到后续
            </button>
            <button class="mini-action-button" type="button" @click="applyCurrentSpaceToLater(activeUploadItem, activeUploadIndex)">
              只套用知识域
            </button>
            <button class="mini-action-button" type="button" @click="clearEmptyMetadataFields(activeUploadItem)">
              清空未填写项
            </button>
          </div>
          <div class="upload-list">
            <article v-for="(item, index) in uploadItems" :key="item.id" class="upload-item" :class="item.status">
              <div class="upload-item-main">
                <div class="upload-file-icon" :class="item.coverTone">
                  <component :is="item.icon" />
                </div>
                <div class="upload-copy">
                  <strong :title="item.name">{{ item.metadata.displayName || item.displayName }}</strong>
                  <span>{{ item.name }} · {{ item.sizeText }} · {{ item.statusText }} · {{ resolveMetadataStatusText(item) }}</span>
                  <em v-if="item.errorMessage">{{ item.errorMessage }}</em>
                  <div v-if="isActiveUploadingItem(item)" class="upload-progress">
                    <i><b :style="{ width: `${item.progress}%` }"></b></i>
                    <small>{{ item.progress }}%</small>
                  </div>
                  <div class="upload-item-actions">
                    <button class="mini-action-button" type="button" @click="toggleUploadItem(item)">
                      {{ item.expanded ? '收起信息' : '展开信息' }}
                    </button>
                    <button class="mini-action-button" type="button" :disabled="item.aiFilling" @click="fillMetadataByAi(item)">
                      <Refresh />
                      {{ item.aiFilling ? '识别中' : 'AI 解析填写' }}
                    </button>
                    <button v-if="item.status === 'failed'" class="mini-action-button" type="button" @click="retryUploadItem(item)">
                      <Refresh />
                      重新上传
                    </button>
                    <button v-if="item.status === 'failed'" class="mini-action-button danger" type="button" @click="discardUploadItem(item)">
                      <Delete />
                      移除
                    </button>
                    <button v-if="item.status === 'waiting'" class="mini-action-button danger" type="button" @click="removeWaitingUploadItem(item)">
                      <Delete />
                      移除
                    </button>
                  </div>
                </div>
              </div>
              <div v-if="item.expanded" class="metadata-panel">
                <div class="metadata-panel-head">
                  <strong>文档信息</strong>
                  <span>第 {{ index + 1 }} 个文件，上传后会按这些信息进入文档库。</span>
                </div>
                <div class="metadata-form">
                  <label>
                    <span>文档名称</span>
                    <el-input v-model="item.metadata.displayName" placeholder="不填则使用原始文件名" />
                  </label>
                  <label>
                    <span>一级知识域</span>
                    <el-select v-model="item.metadata.knowledgeSpaceCode" @change="handleMetadataSpaceChange(item)">
                      <el-option
                        v-for="option in knowledgeSpaceOptions"
                        :key="option.code"
                        :label="option.name"
                        :value="option.code"
                      />
                    </el-select>
                  </label>
                  <label>
                    <span>二级分类</span>
                    <el-select v-model="item.metadata.businessCategoryCode" @change="handleMetadataCategoryChange(item)">
                      <el-option
                        v-for="option in categoryOptionsFor(item.metadata.knowledgeSpaceCode)"
                        :key="option.code"
                        :label="option.name"
                        :value="option.code"
                      />
                    </el-select>
                  </label>
                  <label>
                    <span>文档类型</span>
                    <el-select v-model="item.metadata.documentType">
                      <el-option
                        v-for="option in documentTypeOptions"
                        :key="option.code"
                        :label="option.name"
                        :value="option.code"
                      />
                    </el-select>
                  </label>
                  <label>
                    <span>标签</span>
                    <el-input v-model="item.tagsText" placeholder="多个标签用逗号或回车分隔" @change="syncTagsFromText(item)" />
                  </label>
                  <label>
                    <span>课程/项目</span>
                    <el-input v-model="item.metadata.courseName" placeholder="课程名，可选" />
                  </label>
                  <label>
                    <span>课题/项目名</span>
                    <el-input v-model="item.metadata.projectName" placeholder="课题、项目或申请方向，可选" />
                  </label>
                  <label>
                    <span>学期</span>
                    <el-input v-model="item.metadata.termName" placeholder="如 研一上 / 2026 春" />
                  </label>
                  <label>
                    <span>来源</span>
                    <el-select v-model="item.metadata.sourceType">
                      <el-option
                        v-for="option in sourceTypeOptions"
                        :key="option.code"
                        :label="option.name"
                        :value="option.code"
                      />
                    </el-select>
                  </label>
                  <label class="metadata-note">
                    <span>备注</span>
                    <el-input v-model="item.metadata.note" type="textarea" :rows="2" placeholder="可记录这份资料的用途、老师要求或后续处理提示" />
                  </label>
                </div>
              </div>
            </article>
          </div>
        </div>
        <template #footer>
          <div class="upload-footer-actions">
            <button class="outline-button" type="button" @click="handleUploadDialogClose(() => {})">关闭</button>
            <button class="outline-button" type="button" @click="handleContinueAdd">继续添加</button>
            <button class="primary-button" type="button" :disabled="!canStartUpload" @click="startUploadQueue">
              <UploadFilled />
              {{ uploading ? '上传中' : '开始上传' }}
            </button>
          </div>
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
import { removeFileExtension } from '../utils/fileDisplay'

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
const uploadMetadataOptions = ref(null)

const maxFileSize = 200 * 1024 * 1024
const minChunkSize = 5 * 1024 * 1024
const supportedExtensions = new Set(['pdf', 'txt', 'doc', 'docx', 'ppt', 'pptx', 'wps', 'wpt', 'dps', 'dpt', 'wpd'])
const fallbackMetadataOptions = {
  knowledgeSpaces: [
    { code: 'personal', name: '个人资料库', categories: [{ code: 'general', name: '通用资料' }, { code: 'personal_note', name: '个人笔记' }, { code: 'reading_material', name: '阅读资料' }, { code: 'template', name: '模板' }, { code: 'personal_other', name: '其他' }] },
    { code: 'course', name: '课程学习', categories: [{ code: 'textbook', name: '教材/课本' }, { code: 'lecture_note', name: '课件/讲义' }, { code: 'course_reading', name: '课程阅读材料' }, { code: 'homework', name: '作业/实验' }, { code: 'lab_report', name: '实验报告' }, { code: 'exercise', name: '习题/题库' }, { code: 'course_project', name: '课程项目' }, { code: 'course_other', name: '其他' }] },
    { code: 'research', name: '科研文献', categories: [{ code: 'paper', name: '学术论文' }, { code: 'thesis', name: '学位论文' }, { code: 'review', name: '综述' }, { code: 'book_chapter', name: '专著/章节' }, { code: 'technical_report', name: '技术报告' }, { code: 'patent_standard', name: '专利/标准' }, { code: 'dataset_table', name: '数据集/表格' }, { code: 'reference_bibliography', name: '参考文献清单' }, { code: 'research_other', name: '其他' }] },
    { code: 'writing', name: '写作与规范', categories: [{ code: 'writing_rule', name: '论文/报告写作要求' }, { code: 'format_template', name: '格式模板' }, { code: 'rubric', name: '评分标准' }, { code: 'citation_rule', name: '引用规范' }, { code: 'proposal_requirement', name: '开题/中期要求' }, { code: 'defense_material', name: '答辩材料' }, { code: 'writing_other', name: '其他' }] },
    { code: 'application', name: '申请与事务', categories: [{ code: 'application_form', name: '申请表/报名表' }, { code: 'resume', name: '简历' }, { code: 'personal_statement', name: '个人陈述' }, { code: 'recommendation_letter', name: '推荐信' }, { code: 'certificate', name: '证书/证明' }, { code: 'scholarship', name: '奖学金/资助' }, { code: 'internship_job', name: '实习/就业材料' }, { code: 'visa_admin', name: '签证/行政材料' }, { code: 'application_other', name: '其他' }] },
    { code: 'project', name: '项目与报告', categories: [{ code: 'project_report', name: '项目报告' }, { code: 'research_plan', name: '研究计划' }, { code: 'survey_report', name: '调研报告' }, { code: 'meeting_minutes', name: '会议纪要' }, { code: 'presentation', name: '展示/PPT' }, { code: 'project_dataset', name: '项目数据/表格' }, { code: 'project_requirement', name: '项目要求/说明' }, { code: 'project_other', name: '其他' }] },
    { code: 'exam', name: '考试与复习', categories: [{ code: 'exam_paper', name: '试卷/真题' }, { code: 'review_note', name: '复习资料' }, { code: 'mistake_note', name: '错题整理' }, { code: 'exercise', name: '习题/题库' }, { code: 'exam_outline', name: '考试大纲' }, { code: 'exam_other', name: '其他' }] },
    { code: 'campus_life', name: '校园生活', categories: [{ code: 'schedule_plan', name: '日程/计划' }, { code: 'club_activity', name: '社团/活动' }, { code: 'life_service', name: '生活服务' }, { code: 'finance_receipt', name: '票据/报销' }, { code: 'medical_health', name: '医疗/健康' }, { code: 'campus_other', name: '其他' }] },
  ],
  documentTypes: [
    { code: 'ACADEMIC_PAPER', name: '学术论文' },
    { code: 'THESIS_DISSERTATION', name: '学位论文' },
    { code: 'REVIEW_ARTICLE', name: '综述' },
    { code: 'BOOK_TEXTBOOK', name: '教材/书籍' },
    { code: 'COURSE_MATERIAL', name: '课程资料' },
    { code: 'ASSIGNMENT_HOMEWORK', name: '作业/实验' },
    { code: 'EXAM_REVIEW', name: '考试复习' },
    { code: 'APPLICATION_FORM', name: '申请表' },
    { code: 'RESUME_PROFILE', name: '简历' },
    { code: 'CERTIFICATE_PROOF', name: '证书/证明' },
    { code: 'PROJECT_REPORT', name: '项目报告' },
    { code: 'RESEARCH_PROPOSAL', name: '研究计划/开题' },
    { code: 'WRITING_REQUIREMENT', name: '写作要求' },
    { code: 'PRESENTATION', name: 'PPT/展示' },
    { code: 'SPREADSHEET_TABLE', name: '表格/数据' },
    { code: 'ADMINISTRATIVE_DOCUMENT', name: '行政事务文档' },
    { code: 'LIFE_RECORD', name: '生活记录' },
    { code: 'OTHER_DOCUMENT', name: '其他文档' },
    { code: 'GENERAL_DOCUMENT', name: '通用文档' },
  ],
  sourceTypes: [
    { code: 'USER_UPLOAD', name: '用户上传' },
    { code: 'TEACHER_PROVIDED', name: '老师发放' },
    { code: 'PAPER_DATABASE', name: '论文数据库' },
    { code: 'SELF_ORGANIZED', name: '自己整理' },
    { code: 'OTHER', name: '其他' },
  ],
}

const parsingCount = computed(() => libraryFiles.value.filter((file) => ['PROCESSING', 'PENDING'].includes(file.parseStatus)).length)
const failedCount = computed(() => libraryFiles.value.filter((file) => file.parseStatus === 'FAILED').length)
const metadataOptions = computed(() => uploadMetadataOptions.value || fallbackMetadataOptions)
const knowledgeSpaceOptions = computed(() => metadataOptions.value.knowledgeSpaces || [])
const documentTypeOptions = computed(() => metadataOptions.value.documentTypes || fallbackMetadataOptions.documentTypes)
const sourceTypeOptions = computed(() => metadataOptions.value.sourceTypes || fallbackMetadataOptions.sourceTypes)
const activeUploadIndex = computed(() => {
  const expandedIndex = uploadItems.value.findIndex((item) => item.expanded)
  return expandedIndex >= 0 ? expandedIndex : 0
})
const activeUploadItem = computed(() => uploadItems.value[activeUploadIndex.value] || uploadItems.value[0] || null)
const canStartUpload = computed(() => !uploading.value && uploadItems.value.some((item) => item.status === 'waiting'))

onMounted(() => {
  loadMetadataOptions()
  loadFileList()
})

/**
 * 加载上传元信息选项，失败时保留前端兜底分类。
 */
const loadMetadataOptions = async () => {
  try {
    const response = await fileApi.getUploadMetadataOptions()
    uploadMetadataOptions.value = response.data || fallbackMetadataOptions
  } catch {
    uploadMetadataOptions.value = fallbackMetadataOptions
  }
}

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
  await loadMetadataOptions()
  const recoverableMap = await loadRecoverableUploadMap()
  const rows = files.map((file) => createUploadItem(file, recoverableMap.get(uploadFingerprint(file)), true))
  uploadItems.value = [...uploadItems.value, ...rows]
}

/**
 * 用户点击开始上传后，按当前等待列表顺序上传。
 */
const startUploadQueue = async () => {
  if (!canStartUpload.value) return
  await processUploadQueue()
}

/**
 * 使用前端互斥锁顺序处理上传队列。
 */
const processUploadQueue = async () => {
  if (uploading.value) return
  uploading.value = true
  cancelUploadQueueRequested.value = false
  const pendingItems = uploadItems.value.filter((item) => item.status === 'waiting')
  try {
    for (const item of pendingItems) {
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
      metadata: buildUploadMetadataPayload(item),
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
    item.fileId = response.data?.file?.fileId || response.data?.fileId || item.fileId
    item.status = 'success'
    item.statusText = '已上传，待解析'
    item.progress = 100
    item.metadataStatusText = resolveMetadataStatusText(item)
    ElMessage.success(`${item.metadata.displayName || item.displayName} 上传成功`)
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
 * 只有当前正在上传的文件展示进度条，等待项和已完成项保持列表简洁。
 */
const isActiveUploadingItem = (item) => item.status === 'uploading' && currentUpload.value?.item?.id === item.id

/**
 * 切换上传队列项的元信息表单展开状态。
 */
const toggleUploadItem = (item) => {
  item.expanded = !item.expanded
}

/**
 * 移除尚未开始上传的队列项。
 */
const removeWaitingUploadItem = (item) => {
  uploadItems.value = uploadItems.value.filter((row) => row.id !== item.id)
}

/**
 * 一级知识域变化后，自动选择该知识域下的第一个二级分类。
 */
const handleMetadataSpaceChange = (item) => {
  const space = findKnowledgeSpace(item.metadata.knowledgeSpaceCode)
  item.metadata.knowledgeSpaceName = space?.name || '个人资料库'
  const firstCategory = space?.categories?.[0] || { code: 'general', name: '通用资料' }
  item.metadata.businessCategoryCode = firstCategory.code
  item.metadata.businessCategoryName = firstCategory.name
  item.metadataStatusText = resolveMetadataStatusText(item)
}

/**
 * 二级分类变化后，同步保存分类名称，避免后端只拿到编码。
 */
const handleMetadataCategoryChange = (item) => {
  const category = categoryOptionsFor(item.metadata.knowledgeSpaceCode)
    .find((option) => option.code === item.metadata.businessCategoryCode)
  item.metadata.businessCategoryName = category?.name || '通用资料'
  item.metadataStatusText = resolveMetadataStatusText(item)
}

/**
 * 把标签输入框拆成数组，支持逗号、中文逗号、换行和回车。
 */
const syncTagsFromText = (item) => {
  item.metadata.documentTags = splitTags(item.tagsText)
  item.tagsText = item.metadata.documentTags.join('，')
  item.metadataStatusText = resolveMetadataStatusText(item)
}

/**
 * AI 解析填写按钮：上传前按文件名轻量推荐，上传后优先请求后端建议。
 */
const fillMetadataByAi = async (item) => {
  item.aiFilling = true
  try {
    const suggestion = item.fileId
      ? (await fileApi.suggestMetadata(item.fileId)).data
      : guessMetadataByFileName(item.name)
    applyMetadataSuggestion(item, suggestion)
    item.expanded = true
    item.metadataStatusText = 'AI 已建议'
    ElMessage.success('已生成元信息建议，可继续手动调整')
  } catch (error) {
    ElMessage.warning(error?.message || 'AI 元信息建议暂不可用，已保留手动填写')
  } finally {
    item.aiFilling = false
  }
}

/**
 * 将当前文件的知识域、二级分类和文档类型套用到后续队列项。
 */
const applyCurrentCategoryToLater = (item, index) => {
  if (!item || index < 0) return
  uploadItems.value.slice(index + 1).forEach((row) => {
    row.metadata.knowledgeSpaceCode = item.metadata.knowledgeSpaceCode
    row.metadata.knowledgeSpaceName = item.metadata.knowledgeSpaceName
    row.metadata.businessCategoryCode = item.metadata.businessCategoryCode
    row.metadata.businessCategoryName = item.metadata.businessCategoryName
    row.metadata.documentType = item.metadata.documentType
    row.metadata.sourceType = item.metadata.sourceType
    row.metadataStatusText = resolveMetadataStatusText(row)
  })
  ElMessage.success('已套用分类到后续文件')
}

/**
 * 只把当前一级知识域套用到后续队列项，二级分类按对应知识域默认值重置。
 */
const applyCurrentSpaceToLater = (item, index) => {
  if (!item || index < 0) return
  uploadItems.value.slice(index + 1).forEach((row) => {
    row.metadata.knowledgeSpaceCode = item.metadata.knowledgeSpaceCode
    handleMetadataSpaceChange(row)
  })
  ElMessage.success('已套用知识域到后续文件')
}

/**
 * 清空当前项的可选补充字段，保留名称和分类，方便用户重填。
 */
const clearEmptyMetadataFields = (item) => {
  if (!item) return
  item.metadata.documentTags = []
  item.metadata.courseName = ''
  item.metadata.projectName = ''
  item.metadata.termName = ''
  item.metadata.note = ''
  item.tagsText = ''
  item.metadataStatusText = resolveMetadataStatusText(item)
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
  link.download = file.downloadName || file.name
  link.click()
  window.URL.revokeObjectURL(url)
}

/**
 * 删除文档。
 */
const deleteFile = async (file) => {
  await ElMessageBox.confirm(`确定删除「${file.displayName}」吗？`, '删除文档', {
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
    await ElMessageBox.confirm(`解析失败，是否重新解析「${file.displayName}」？`, '重新解析', {
      confirmButtonText: '重新解析',
      cancelButtonText: '取消',
      type: 'warning',
    })
  }
  notifyUserOperationChanged()
  sessionStorage.setItem('docnexusParsePreviewFile', JSON.stringify({
    fileId: file.fileId,
    name: file.name,
    typeLabel: file.typeLabel,
    time: file.time,
    parseStatus: file.parseStatus,
  }))
  router.push(`/knowledge/parse-workspace/${file.fileId}`)
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
const createUploadItem = (file, recoverable = null, expanded = false) => {
  const meta = resolveTypeMeta(file.name)
  const metadata = createDefaultMetadata(file, recoverable)
  const uploadedChunks = recoverable?.uploadedChunks || 0
  const totalChunks = recoverable?.totalChunks || 0
  return {
    id: `upload_${Date.now()}_${Math.random().toString(16).slice(2)}`,
    file,
    name: file.name,
    displayName: metadata.displayName || removeFileExtension(file.name),
    expanded,
    aiFilling: false,
    fileId: recoverable?.fileId || '',
    metadata,
    tagsText: (metadata.documentTags || []).join('，'),
    metadataStatusText: resolveMetadataStatusText({ metadata, name: file.name }),
    sizeText: formatFileSize(file.size),
    status: 'waiting',
    statusText: recoverable?.uploadId ? '已找到断点，等待续传' : '等待上传',
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
 * 创建上传阶段元信息草稿。
 *
 * 默认只填系统兜底值，不根据文件名自动猜论文、作业等类型；否则用户没有填写也会被误判为已填写。
 * 真正的智能补全只在用户点击“AI 解析填写”或后续解析 Agent 运行时发生。
 */
const createDefaultMetadata = (file, recoverable = null) => {
  const recoveredDraft = parseMetadataDraft(recoverable?.metadataDraftJson)
  return normalizeUploadMetadata({
    ...recoveredDraft,
    displayName: recoveredDraft.displayName || removeFileExtension(file.name),
  }, file.name)
}

/**
 * 整理提交给后端的上传元信息，保证名称、编码和名称同时存在。
 */
const buildUploadMetadataPayload = (item) => {
  syncTagsFromText(item)
  const normalized = normalizeUploadMetadata(item.metadata, item.name)
  item.metadata = normalized
  return normalized
}

/**
 * 规范化上传元信息草稿，补齐默认知识域、分类和文档类型。
 */
const normalizeUploadMetadata = (metadata = {}, fileName = '') => {
  const spaceCode = metadata.knowledgeSpaceCode || 'personal'
  const space = findKnowledgeSpace(spaceCode) || findKnowledgeSpace('personal')
  const categories = space?.categories?.length ? space.categories : [{ code: 'general', name: '通用资料' }]
  const category = categories.find((item) => item.code === metadata.businessCategoryCode) || categories[0]
  return {
    displayName: metadata.displayName || removeFileExtension(fileName),
    knowledgeSpaceCode: space?.code || 'personal',
    knowledgeSpaceName: metadata.knowledgeSpaceName || space?.name || '个人资料库',
    businessCategoryCode: category?.code || 'general',
    businessCategoryName: metadata.businessCategoryName || category?.name || '通用资料',
    documentType: metadata.documentType || 'GENERAL_DOCUMENT',
    documentTags: Array.isArray(metadata.documentTags) ? metadata.documentTags : splitTags(metadata.documentTags || ''),
    courseName: metadata.courseName || '',
    projectName: metadata.projectName || '',
    termName: metadata.termName || '',
    sourceType: metadata.sourceType || 'USER_UPLOAD',
    note: metadata.note || '',
  }
}

/**
 * 尝试恢复上传会话中的元信息草稿 JSON。
 */
const parseMetadataDraft = (value) => {
  if (!value) return {}
  if (typeof value === 'object') return value
  try {
    return JSON.parse(value)
  } catch {
    return {}
  }
}

/**
 * 根据文件名和扩展名给出本地轻量分类建议。
 */
const guessMetadataByFileName = (fileName = '') => {
  const lower = fileName.toLowerCase()
  const ext = resolveExtension(fileName)
  const base = {
    displayName: removeFileExtension(fileName),
    knowledgeSpaceCode: 'personal',
    businessCategoryCode: 'general',
    documentType: 'GENERAL_DOCUMENT',
    documentTags: [],
    sourceType: 'USER_UPLOAD',
  }
  if (['ppt', 'pptx', 'dps', 'dpt'].includes(ext)) {
    return withNames({ ...base, knowledgeSpaceCode: 'project', businessCategoryCode: 'presentation', documentType: 'PRESENTATION', documentTags: ['展示'] })
  }
  if (lower.includes('论文') || lower.includes('paper') || lower.includes('doi')) {
    return withNames({ ...base, knowledgeSpaceCode: 'research', businessCategoryCode: 'paper', documentType: 'ACADEMIC_PAPER', documentTags: ['论文'] })
  }
  if (lower.includes('作业') || lower.includes('实验') || lower.includes('homework')) {
    return withNames({ ...base, knowledgeSpaceCode: 'course', businessCategoryCode: 'homework', documentType: 'ASSIGNMENT_HOMEWORK', documentTags: ['作业'] })
  }
  if (lower.includes('申请') || lower.includes('报名') || lower.includes('表')) {
    return withNames({ ...base, knowledgeSpaceCode: 'application', businessCategoryCode: 'application_form', documentType: 'APPLICATION_FORM', documentTags: ['申请'] })
  }
  if (lower.includes('简历') || lower.includes('resume') || lower.includes('cv')) {
    return withNames({ ...base, knowledgeSpaceCode: 'application', businessCategoryCode: 'resume', documentType: 'RESUME_PROFILE', documentTags: ['简历'] })
  }
  if (lower.includes('要求') || lower.includes('规范') || lower.includes('格式')) {
    return withNames({ ...base, knowledgeSpaceCode: 'writing', businessCategoryCode: 'writing_rule', documentType: 'WRITING_REQUIREMENT', documentTags: ['写作要求'] })
  }
  return withNames(base)
}

/**
 * 合并 AI 建议到当前草稿。
 */
const applyMetadataSuggestion = (item, suggestion = {}) => {
  const next = normalizeUploadMetadata({
    ...item.metadata,
    ...suggestion,
    documentTags: suggestion.documentTags || item.metadata.documentTags,
  }, item.name)
  item.metadata = next
  item.tagsText = (next.documentTags || []).join('，')
}

/**
 * 给仅有编码的建议补充中文名称。
 */
const withNames = (metadata) => {
  const normalized = normalizeUploadMetadata(metadata, metadata.displayName || '')
  return {
    ...metadata,
    knowledgeSpaceName: normalized.knowledgeSpaceName,
    businessCategoryName: normalized.businessCategoryName,
  }
}

/**
 * 查找一级知识域选项。
 */
const findKnowledgeSpace = (code) => knowledgeSpaceOptions.value.find((item) => item.code === code)

/**
 * 获取某个一级知识域下的二级分类。
 */
const categoryOptionsFor = (spaceCode) => {
  const space = findKnowledgeSpace(spaceCode) || findKnowledgeSpace('personal')
  return space?.categories?.length ? space.categories : [{ code: 'general', name: '通用资料' }]
}

/**
 * 拆分标签文本。
 */
const splitTags = (value) => {
  if (Array.isArray(value)) return value.filter(Boolean)
  return String(value || '')
    .split(/[，,\n\r]+/)
    .map((item) => item.trim())
    .filter(Boolean)
}

/**
 * 判断元信息是否仍为默认跳过状态。
 */
const resolveMetadataStatusText = (item) => {
  const metadata = item?.metadata || {}
  const hasCustomDisplay = metadata.displayName && metadata.displayName !== removeFileExtension(item?.name || '')
  const hasCustomTaxonomy = metadata.knowledgeSpaceCode !== 'personal'
    || metadata.businessCategoryCode !== 'general'
    || metadata.documentType !== 'GENERAL_DOCUMENT'
  const hasExtra = splitTags(metadata.documentTags).length > 0
    || Boolean(metadata.courseName)
    || Boolean(metadata.projectName)
    || Boolean(metadata.termName)
    || Boolean(metadata.note)
    || (metadata.sourceType && metadata.sourceType !== 'USER_UPLOAD')
  return hasCustomDisplay || hasCustomTaxonomy || hasExtra ? '已填写元信息' : '未填写，使用默认'
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
  const originalName = file.originalName || file.name || '未命名文档'
  const displayName = file.displayName || file.name || removeFileExtension(originalName)
  const meta = resolveTypeMeta(originalName)
  return {
    fileId: file.fileId || file.id,
    name: originalName,
    displayName,
    downloadName: withOriginalExtension(displayName, originalName),
    knowledgeSpaceName: file.knowledgeSpaceName || '个人资料库',
    businessCategoryName: file.businessCategoryName || '通用资料',
    documentType: file.documentType || 'GENERAL_DOCUMENT',
    metadataStatus: file.metadataStatus || 'USER_SKIPPED',
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
  if (['wps', 'wpt', 'wpd'].includes(ext)) return { icon: markRaw(Document), typeLabel: 'WORD', coverTone: 'wps' }
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
 * 下载名使用展示名，但保留原始扩展名，避免下载后失去文件类型。
 */
const withOriginalExtension = (displayName, originalName) => {
  const ext = resolveExtension(originalName)
  if (!ext || String(displayName || '').toLowerCase().endsWith(`.${ext}`)) return displayName || originalName
  return `${displayName || removeFileExtension(originalName)}.${ext}`
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

.primary-button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
  box-shadow: none;
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

:global(.el-dropdown-menu__item.file-menu-item) {
  min-width: 158px;
  height: 44px;
  margin: 4px 6px;
  padding: 0 12px;
  border-radius: 8px;
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  color: #455a64;
  font-size: 15px;
  font-weight: 700;
  line-height: 1;
}

:global(.el-dropdown-menu__item.file-menu-item svg) {
  width: 18px;
  height: 18px;
  padding: 5px;
  border-radius: 8px;
  background: #edf4fb;
  color: #3b82d6;
  box-sizing: content-box;
}

:global(.el-dropdown-menu__item.file-menu-item span) {
  line-height: 1;
  white-space: nowrap;
}

:global(.el-dropdown-menu__item.download-menu-item) {
  color: #2f73c8;
}

:global(.el-dropdown-menu__item.download-menu-item:hover),
:global(.el-dropdown-menu__item.download-menu-item:focus) {
  background: #edf6ff;
  color: #155eaf;
}

:global(.el-dropdown-menu__item.download-menu-item svg) {
  background: #e5f1ff;
  color: #2f80ed;
}

:global(.el-dropdown-menu__item.parse-menu-item) {
  color: #00765f;
  background: linear-gradient(135deg, rgba(232, 248, 242, 0.95), rgba(255, 255, 255, 0.92));
}

:global(.el-dropdown-menu__item.parse-menu-item:hover),
:global(.el-dropdown-menu__item.parse-menu-item:focus) {
  color: #005f4e;
  background: linear-gradient(135deg, #ddf6ed, #f4fffb);
}

:global(.el-dropdown-menu__item.parse-menu-item svg) {
  background: #dff7ee;
  color: #008d72;
}

:global(.el-dropdown-menu__item.danger-item) {
  color: #b94a43;
}

:global(.el-dropdown-menu__item.danger-item:hover),
:global(.el-dropdown-menu__item.danger-item:focus) {
  background: #fff1ef;
  color: #a7362f;
}

:global(.el-dropdown-menu__item.danger-item svg) {
  background: #fff0ee;
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
  max-height: 560px;
  overflow: auto;
}

.upload-item {
  padding: 12px;
  border: 1px solid #e0ebe7;
  border-radius: 8px;
  background: #fff;
}

.upload-item-main {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr);
  gap: 12px;
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
  flex-wrap: wrap;
}

.upload-batch-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.upload-footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
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

.mini-action-button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
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

.metadata-panel {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #d8e7e2;
}

.metadata-panel-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.metadata-panel-head strong {
  color: #143f35;
}

.metadata-panel-head span {
  color: #6c7f79;
  font-size: 13px;
}

.metadata-form {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.metadata-form label {
  min-width: 0;
  display: grid;
  gap: 6px;
}

.metadata-form label > span {
  color: #40554f;
  font-size: 13px;
  font-weight: 700;
}

.metadata-note {
  grid-column: 1 / -1;
}

.metadata-form :deep(.el-select),
.metadata-form :deep(.el-input),
.metadata-form :deep(.el-textarea) {
  width: 100%;
}

@media (max-width: 860px) {
  .metadata-form {
    grid-template-columns: 1fr;
  }

  .metadata-panel-head {
    display: grid;
  }
}
</style>
