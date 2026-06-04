<template>
  <div class="file-editor-page">
    <header class="editor-toolbar">
      <div class="toolbar-left">
        <button class="icon-button" type="button" title="返回文档库" @click="goBack">
          <ArrowLeft />
        </button>
        <div class="title-block">
          <strong :title="docName">{{ docName }}</strong>
          <span>{{ saveStatusText }}</span>
        </div>
      </div>
      <div class="toolbar-actions">
        <button class="save-button" type="button" :disabled="!editable || isSaving" @click="handleManualSave">
          <Check />
          {{ isSaving ? '保存中' : '手动保存' }}
        </button>
        <button class="download-button" type="button" @click="handleDownload">
          <Download />
          下载
        </button>
        <button class="panel-toggle" type="button" @click="toggleAssistantPanel">
          <MagicStick />
          {{ isAssistantCollapsed ? '展开助手' : '收起助手' }}
        </button>
        <button class="disabled-share" type="button" disabled title="暂不支持分享">
          <Share />
          协作
        </button>
        <span class="user-avatar">{{ userInitial }}</span>
      </div>
    </header>

    <main
      :class="['editor-workspace', { 'assistant-collapsed': isAssistantCollapsed }]"
      :style="workspaceStyle"
    >
      <section v-loading="loading" class="document-canvas">
        <div v-if="isPdf" class="pdf-preview">
          <iframe v-if="previewUrl" :src="previewUrl" title="PDF 预览"></iframe>
          <div v-else class="preview-empty">PDF 预览加载中</div>
        </div>
        <div v-else-if="isOnlyOffice" class="onlyoffice-shell">
          <div :id="onlyOfficeContainerId" class="onlyoffice-editor"></div>
          <div v-if="onlyOfficeDiagnostic" class="onlyoffice-diagnostic">
            <strong>OnlyOffice 暂未加载完成</strong>
            <p>{{ onlyOfficeDiagnostic }}</p>
          </div>
        </div>
        <article v-else class="paper">
          <div
            ref="paperRef"
            class="paper-content"
            :contenteditable="editable"
            v-html="docContent"
            @input="handleInput"
          ></div>
        </article>
      </section>

      <button
        v-show="!isAssistantCollapsed"
        class="assistant-resizer"
        type="button"
        title="拖动调整助手宽度"
        @mousedown="startAssistantResize"
      ></button>

      <aside v-show="!isAssistantCollapsed" class="ai-panel">
        <section class="ai-panel-section">
          <h2><MagicStick /> AI 灵感助理</h2>
          <div class="stats-grid">
            <div>
              <strong>{{ textStats.totalCharacters }}</strong>
              <span>总字符数</span>
            </div>
            <div>
              <strong>{{ textStats.paragraphs }}</strong>
              <span>段落数</span>
            </div>
            <div>
              <strong>{{ textStats.chineseCharacters }}</strong>
              <span>中文字符</span>
            </div>
            <div>
              <strong>{{ textStats.punctuations }}</strong>
              <span>标点符号</span>
            </div>
          </div>
        </section>

        <section class="summary-card">
          <strong>智能摘要</strong>
          <p>当前页面使用 OnlyOffice 保留原文件格式、图片和版式；AI 摘要、润色和问答能力将在解析服务完成后接入。</p>
        </section>

        <section class="assistant-placeholder">
          <p>你好！我是文档助理。后续你可以选中文本进行润色、纠错，或基于文档内容提问。</p>
        </section>

        <div class="assistant-input">
          <label>
            <input type="checkbox" disabled />
            基于文档库问答
          </label>
          <textarea disabled placeholder="问问 AI..."></textarea>
          <button type="button" disabled>发送指令</button>
        </div>
      </aside>
    </main>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Check, Download, MagicStick, Share } from '@element-plus/icons-vue'
import { fileApi } from '../api/file'
import { STORAGE_KEYS } from '../constants'
import { notifyUserOperationChanged } from '../utils/sidebarStats'

const ONLYOFFICE_API_TIMEOUT_MS = 15000
const ONLYOFFICE_READY_TIMEOUT_MS = 25000
const route = useRoute()
const router = useRouter()
const fileId = computed(() => route.params.id)
const paperRef = ref(null)
const loading = ref(false)
const isSaving = ref(false)
const docName = ref('正在加载文档')
const docContent = ref('')
const previewUrl = ref('')
const editable = ref(false)
const isPdf = ref(false)
const isOnlyOffice = ref(false)
const onlyOfficeDirty = ref(false)
const onlyOfficeBackendDirty = ref(false)
const onlyOfficeEditor = ref(null)
const onlyOfficeDiagnostic = ref('')
const onlyOfficeDocumentKey = ref('')
const isAssistantCollapsed = ref(false)
const assistantWidth = ref(Math.round(window.innerWidth * 0.34))
const isResizingAssistant = ref(false)
const onlyOfficeContainerId = `onlyoffice-editor-${Math.random().toString(16).slice(2)}`
let onlyOfficeReadyTimer = null
let resizeStartX = 0
let resizeStartWidth = 0
const currentVersion = ref(1)
const initialContentHash = ref('')
const currentContentHash = ref('')
const parseStatus = ref('PENDING')
const indexStatus = ref('NONE')
const saveStatusText = ref('加载中')

const username = computed(() => {
  try {
    const user = JSON.parse(localStorage.getItem(STORAGE_KEYS.USER_INFO) || '{}')
    return user.displayName || user.username || localStorage.getItem('userName') || 'DocNexus'
  } catch {
    return localStorage.getItem('userName') || 'DocNexus'
  }
})

const userInitial = computed(() => username.value.trim().charAt(0).toUpperCase() || '文')

const textStats = computed(() => {
  const text = getPlainText()
  return {
    totalCharacters: text.length,
    paragraphs: text.split(/\n+/).filter((line) => line.trim()).length,
    chineseCharacters: (text.match(/[\u4e00-\u9fa5]/g) || []).length,
    punctuations: (text.match(/[，。！？、；：“”‘’（）,.!?;:]/g) || []).length,
  }
})

const workspaceStyle = computed(() => {
  if (isAssistantCollapsed.value) {
    return {}
  }
  return {
    '--assistant-width': `${assistantWidth.value}px`,
  }
})

onMounted(() => {
  loadEditor()
})

onBeforeUnmount(() => {
  if (previewUrl.value) {
    window.URL.revokeObjectURL(previewUrl.value)
  }
  stopAssistantResize()
  destroyOnlyOfficeEditor()
})

/**
 * 加载真实文档内容。
 */
const loadEditor = async () => {
  loading.value = true
  destroyOnlyOfficeEditor()
  isOnlyOffice.value = false
  onlyOfficeDirty.value = false
  onlyOfficeBackendDirty.value = false
  onlyOfficeDocumentKey.value = ''
  onlyOfficeDiagnostic.value = ''
  try {
    await loadOnlyOfficeEditor()
  } catch (error) {
    await loadFallbackEditor(error)
  } finally {
    loading.value = false
  }
}

/**
 * 加载 OnlyOffice 原格式编辑器。
 */
const loadOnlyOfficeEditor = async () => {
  const response = await fileApi.getOnlyOfficeConfig(fileId.value, { silent: true })
  const data = response.data || {}
  docName.value = data.originalName || '未命名文档'
  editable.value = Boolean(data.editable)
  currentVersion.value = Number(data.currentVersion || 1)
  onlyOfficeDocumentKey.value = data.documentKey || ''
  initialContentHash.value = data.documentKey || ''
  currentContentHash.value = data.documentKey || ''
  parseStatus.value = data.parseStatus || 'PENDING'
  indexStatus.value = data.indexStatus || 'NONE'
  isPdf.value = false
  isOnlyOffice.value = true
  docContent.value = ''
  onlyOfficeDiagnostic.value = resolveOnlyOfficeDiagnostic(data.diagnostics)
  saveStatusText.value = 'OnlyOffice 加载中'
  await nextTick()
  await loadOnlyOfficeApi(data.documentServerApiUrl)
  initOnlyOfficeEditor(data.config || {})
}

/**
 * 加载旧的真实文本预览兜底。
 */
const loadFallbackEditor = async (originalError) => {
  try {
    const response = await fileApi.openEditor(fileId.value)
    const data = response.data || {}
    docName.value = data.originalName || '未命名文档'
    editable.value = Boolean(data.editable)
    currentVersion.value = Number(data.currentVersion || 1)
    initialContentHash.value = data.contentHash || ''
    currentContentHash.value = data.contentHash || ''
    parseStatus.value = data.parseStatus || 'PENDING'
    indexStatus.value = data.indexStatus || 'NONE'
    isPdf.value = String(data.fileExt || '').toLowerCase() === 'pdf'
    isOnlyOffice.value = false
    saveStatusText.value = editable.value ? '已同步' : '只读预览'

    if (isPdf.value) {
      await loadPdfPreview()
      docContent.value = ''
      return
    }
    docContent.value = data.contentHtml || '<p>暂无可展示文本内容</p>'
  } catch (error) {
    docName.value = '文档打开失败'
    docContent.value = '<p>读取文档失败，请确认该格式是否已支持真实内容转换。</p>'
    saveStatusText.value = '打开失败'
    ElMessage.error(error?.message || originalError?.message || '文档打开失败')
  }
}

/**
 * 动态加载 OnlyOffice DocsAPI。
 */
const loadOnlyOfficeApi = (apiUrl) => {
  if (window.DocsAPI?.DocEditor) return Promise.resolve()
  if (!apiUrl) return Promise.reject(new Error('OnlyOffice API 地址为空，请检查后端 onlyoffice.public-url 配置'))
  return new Promise((resolve, reject) => {
    let settled = false
    const finish = (handler) => {
      if (settled) return
      settled = true
      window.clearTimeout(timeoutId)
      handler()
    }
    const resolveAfterCheck = () => {
      if (window.DocsAPI?.DocEditor) {
        finish(resolve)
        return
      }
      finish(() => reject(new Error('OnlyOffice API 已加载，但 DocsAPI 不存在，请检查 Document Server 是否完整启动')))
    }
    const timeoutId = window.setTimeout(() => {
      finish(() => reject(new Error('OnlyOffice API 加载超时，请确认浏览器能访问 Document Server')))
    }, ONLYOFFICE_API_TIMEOUT_MS)
    const existed = document.getElementById('onlyoffice-docs-api')
    if (existed) {
      if (existed.dataset.loaded === 'true') {
        resolveAfterCheck()
        return
      }
      if (existed.dataset.failed === 'true') {
        existed.remove()
      } else {
        existed.addEventListener('load', resolveAfterCheck, { once: true })
        existed.addEventListener('error', () => {
          existed.dataset.failed = 'true'
          finish(() => reject(new Error('OnlyOffice API 加载失败，请确认 Document Server 已启动')))
        }, { once: true })
        return
      }
    }
    const script = document.createElement('script')
    script.id = 'onlyoffice-docs-api'
    script.src = apiUrl
    script.onload = () => {
      script.dataset.loaded = 'true'
      resolveAfterCheck()
    }
    script.onerror = () => {
      script.dataset.failed = 'true'
      finish(() => reject(new Error('OnlyOffice API 加载失败，请确认 Document Server 已启动')))
    }
    document.head.appendChild(script)
  })
}

/**
 * 启动 OnlyOffice 就绪超时检查，避免页面长期停在加载骨架屏。
 */
const startOnlyOfficeReadyWatchdog = () => {
  clearOnlyOfficeReadyWatchdog()
  onlyOfficeReadyTimer = window.setTimeout(() => {
    saveStatusText.value = 'OnlyOffice 回源超时'
    onlyOfficeDiagnostic.value = onlyOfficeDiagnostic.value || '后端配置已经返回，但 Document Server 没有成功打开文档。请检查 ONLYOFFICE_CALLBACK_BASE_URL 是否能被 Document Server 访问，并确认 IDEA 控制台是否出现“OnlyOffice 源文件接口收到请求”。'
  }, ONLYOFFICE_READY_TIMEOUT_MS)
}

/**
 * 清理 OnlyOffice 就绪超时检查。
 */
const clearOnlyOfficeReadyWatchdog = () => {
  if (onlyOfficeReadyTimer) {
    window.clearTimeout(onlyOfficeReadyTimer)
    onlyOfficeReadyTimer = null
  }
}

/**
 * 标记 OnlyOffice 已经进入可用状态。
 */
const markOnlyOfficeReady = () => {
  clearOnlyOfficeReadyWatchdog()
  onlyOfficeDiagnostic.value = ''
  saveStatusText.value = '已同步'
}

/**
 * 展示 OnlyOffice 编辑器错误。
 */
const showOnlyOfficeError = (message) => {
  clearOnlyOfficeReadyWatchdog()
  const finalMessage = message || 'OnlyOffice 编辑器异常'
  onlyOfficeDiagnostic.value = finalMessage
  saveStatusText.value = '编辑器异常'
  ElMessage.error(finalMessage)
}

/**
 * 根据后端诊断结果生成 OnlyOffice 加载提示。
 */
const resolveOnlyOfficeDiagnostic = (diagnostics) => {
  if (!diagnostics || typeof diagnostics !== 'object') return ''
  if (diagnostics.message) return diagnostics.message
  if (diagnostics.apiReachable === false) return 'OnlyOffice API 脚本不可访问，请检查 Document Server 地址。'
  if (diagnostics.backendExposed === false) return 'OnlyOffice 后端接口未完整暴露，请检查服务器反向代理或端口映射。'
  return ''
}

/**
 * 初始化 OnlyOffice 编辑器。
 */
const initOnlyOfficeEditor = (config) => {
  destroyOnlyOfficeEditor()
  startOnlyOfficeReadyWatchdog()
  const editorConfig = {
    ...config,
    events: {
      onAppReady: () => {
        saveStatusText.value = 'OnlyOffice 已连接'
      },
      onDocumentReady: () => {
        markOnlyOfficeReady()
      },
      onDocumentStateChange: (event) => {
        onlyOfficeDirty.value = Boolean(event?.data)
        if (onlyOfficeDirty.value) {
          onlyOfficeBackendDirty.value = true
        }
        saveStatusText.value = onlyOfficeBackendDirty.value ? '修改待保存' : '已同步'
      },
      onError: (event) => {
        showOnlyOfficeError(event?.data?.message || 'OnlyOffice 编辑器异常')
      },
    },
  }
  onlyOfficeEditor.value = new window.DocsAPI.DocEditor(onlyOfficeContainerId, editorConfig)
}

/**
 * 销毁 OnlyOffice 编辑器实例。
 */
const destroyOnlyOfficeEditor = () => {
  clearOnlyOfficeReadyWatchdog()
  if (onlyOfficeEditor.value?.destroyEditor) {
    onlyOfficeEditor.value.destroyEditor()
  }
  onlyOfficeEditor.value = null
  onlyOfficeDiagnostic.value = ''
}

/**
 * 加载 PDF 真实预览流。
 */
const loadPdfPreview = async () => {
  const blob = await fileApi.preview(fileId.value)
  if (previewUrl.value) {
    window.URL.revokeObjectURL(previewUrl.value)
  }
  previewUrl.value = window.URL.createObjectURL(blob)
}

/**
 * 手动保存文档内容。
 */
const handleManualSave = async () => {
  if (isOnlyOffice.value) {
    if (!onlyOfficeBackendDirty.value) {
      saveStatusText.value = '已同步'
      ElMessage.info('用户未更改，无需保存')
      return
    }
    isSaving.value = true
    saveStatusText.value = '正在保存...'
    try {
      notifyUserOperationChanged()
      const response = await fileApi.forceSaveOnlyOffice(fileId.value, {
        currentVersion: currentVersion.value,
        documentKey: onlyOfficeDocumentKey.value,
      })
      const data = response.data || {}
      if (data.saved === false) {
        onlyOfficeDirty.value = false
        onlyOfficeBackendDirty.value = false
        saveStatusText.value = '已同步'
        ElMessage.info(data.message || '用户未更改，无需保存')
        return
      }
      currentVersion.value = Number(data.currentVersion || currentVersion.value + 1)
      initialContentHash.value = data.contentHash || initialContentHash.value
      currentContentHash.value = initialContentHash.value
      onlyOfficeDirty.value = false
      onlyOfficeBackendDirty.value = false
      saveStatusText.value = '已同步'
      ElMessage.success('已保存成功')
      await loadEditor()
    } catch (error) {
      saveStatusText.value = '保存失败'
      ElMessage.error(error?.message || '保存失败')
    } finally {
      isSaving.value = false
    }
    return
  }
  if (!editable.value || isPdf.value) {
    ElMessage.warning('当前文档暂不支持在线保存')
    return
  }
  const contentHtml = getCurrentHtml()
  const nextHash = await sha256(contentHtml)
  currentContentHash.value = nextHash
  if (nextHash === initialContentHash.value) {
    ElMessage.info('没有检测到修改')
    saveStatusText.value = '已同步'
    return
  }

  isSaving.value = true
  saveStatusText.value = '正在保存...'
  try {
    notifyUserOperationChanged()
    const response = await fileApi.saveEditorContent(fileId.value, {
      currentVersion: currentVersion.value,
      contentHtml,
      contentHash: nextHash,
    })
    const data = response.data || {}
    currentVersion.value = Number(data.currentVersion || currentVersion.value + 1)
    initialContentHash.value = data.contentHash || nextHash
    currentContentHash.value = initialContentHash.value
    parseStatus.value = data.parseStatus || 'PENDING'
    indexStatus.value = data.indexStatus || 'NONE'
    saveStatusText.value = '已同步'
    ElMessage.success('保存成功，已提交重新解析')
  } catch (error) {
    saveStatusText.value = '保存失败'
    ElMessage.error(error?.message || '保存失败')
  } finally {
    isSaving.value = false
  }
}

/**
 * 下载当前 MinIO 中的版本。
 */
const handleDownload = async () => {
  if (isOnlyOffice.value && onlyOfficeBackendDirty.value) {
    ElMessageBox.alert('检测到当前文档有更新，请保存后再下载', '请先保存', {
      confirmButtonText: '知道了',
      type: 'warning',
    })
    return
  }
  const dirty = await hasUnsavedChanges()
  if (dirty) {
    ElMessageBox.alert('检测到当前文档有更新，请保存后再下载', '请先保存', {
      confirmButtonText: '知道了',
      type: 'warning',
    })
    return
  }
  notifyUserOperationChanged()
  const blob = await fileApi.download(fileId.value)
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = docName.value
  link.click()
  window.URL.revokeObjectURL(url)
}

/**
 * 标记内容已修改。
 */
const handleInput = async () => {
  if (!editable.value) return
  saveStatusText.value = '修改未保存'
  currentContentHash.value = await sha256(getCurrentHtml())
}

/**
 * 返回文档库。
 */
const goBack = () => {
  router.push('/knowledge')
}

/**
 * 切换右侧 AI 助手面板。
 */
const toggleAssistantPanel = () => {
  isAssistantCollapsed.value = !isAssistantCollapsed.value
  window.setTimeout(() => {
    window.dispatchEvent(new Event('resize'))
  }, 160)
}

/**
 * 开始拖拽调整 AI 助手宽度。
 */
const startAssistantResize = (event) => {
  if (isAssistantCollapsed.value) return
  isResizingAssistant.value = true
  resizeStartX = event.clientX
  resizeStartWidth = assistantWidth.value
  document.body.classList.add('resizing-assistant-panel')
  window.addEventListener('mousemove', handleAssistantResize)
  window.addEventListener('mouseup', stopAssistantResize)
}

/**
 * 根据鼠标横向移动实时计算右侧助手宽度。
 */
const handleAssistantResize = (event) => {
  if (!isResizingAssistant.value) return
  const delta = resizeStartX - event.clientX
  const nextWidth = resizeStartWidth + delta
  const maxWidth = Math.min(760, Math.floor(window.innerWidth * 0.46))
  const minWidth = Math.max(360, Math.floor(window.innerWidth * 0.24))
  assistantWidth.value = Math.min(maxWidth, Math.max(minWidth, nextWidth))
  window.dispatchEvent(new Event('resize'))
}

/**
 * 停止拖拽并恢复页面选择状态。
 */
const stopAssistantResize = () => {
  if (!isResizingAssistant.value) return
  isResizingAssistant.value = false
  document.body.classList.remove('resizing-assistant-panel')
  window.removeEventListener('mousemove', handleAssistantResize)
  window.removeEventListener('mouseup', stopAssistantResize)
}

/**
 * 判断是否存在未保存修改。
 */
const hasUnsavedChanges = async () => {
  if (isOnlyOffice.value) return onlyOfficeBackendDirty.value
  if (!editable.value || isPdf.value) return false
  const nextHash = await sha256(getCurrentHtml())
  currentContentHash.value = nextHash
  return nextHash !== initialContentHash.value
}

/**
 * 获取当前编辑区 HTML。
 */
const getCurrentHtml = () => {
  return paperRef.value?.innerHTML || docContent.value || ''
}

/**
 * 获取当前编辑区纯文本。
 */
const getPlainText = () => {
  if (isOnlyOffice.value) return ''
  if (paperRef.value) return paperRef.value.innerText || ''
  return String(docContent.value || '').replace(/<[^>]+>/g, '\n')
}

/**
 * 计算前端内容 SHA-256，用于保存前比较是否修改。
 */
const sha256 = async (text) => {
  const buffer = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(text || ''))
  return Array.from(new Uint8Array(buffer)).map((byte) => byte.toString(16).padStart(2, '0')).join('')
}
</script>

<style scoped>
.file-editor-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #eef5f2;
  color: #10251f;
}

.editor-toolbar {
  height: 64px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  background: rgba(255, 255, 255, 0.94);
  border-bottom: 1px solid #dbe8e3;
}

.toolbar-left,
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.icon-button {
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #57706a;
  cursor: pointer;
}

.icon-button:hover {
  background: #eff7f4;
  color: #008d72;
}

.icon-button svg {
  width: 18px;
  height: 18px;
}

.title-block {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.title-block strong {
  max-width: 560px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 18px;
  color: #12352e;
}

.title-block span {
  color: #008d72;
  font-size: 13px;
  padding: 4px 9px;
  border-radius: 999px;
  background: #e7f6f1;
}

.save-button,
.download-button,
.panel-toggle,
.disabled-share {
  height: 34px;
  border-radius: 999px;
  padding: 0 14px;
  border: 1px solid transparent;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 700;
  cursor: pointer;
}

.save-button {
  background: #008d72;
  color: #fff;
  box-shadow: 0 8px 18px rgba(0, 141, 114, 0.18);
}

.save-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.download-button {
  background: #fff;
  color: #0a6d5a;
  border-color: #cce5dd;
}

.panel-toggle {
  background: #f6fbf9;
  color: #0a6d5a;
  border-color: #cce5dd;
}

.panel-toggle:hover {
  background: #e8f6f1;
  border-color: #97d4c4;
}

.disabled-share {
  background: #f2f5f4;
  color: #98a6a2;
  cursor: not-allowed;
}

.save-button svg,
.download-button svg,
.panel-toggle svg,
.disabled-share svg {
  width: 15px;
  height: 15px;
}

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #008d72;
  color: #fff;
  font-weight: 800;
}

.editor-workspace {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 8px var(--assistant-width, 34vw);
  overflow: hidden;
}

.editor-workspace.assistant-collapsed {
  grid-template-columns: minmax(0, 1fr);
}

.document-canvas {
  overflow: auto;
  padding: 18px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.2), rgba(0, 141, 114, 0.04)),
    #edf2f1;
}

.paper {
  width: min(820px, calc(100vw - 500px));
  min-height: 1120px;
  margin: 0 auto;
  background: #fff;
  box-shadow: 0 18px 48px rgba(40, 70, 62, 0.12);
  border: 1px solid #e5ede9;
}

.paper-content {
  min-height: 1120px;
  padding: 78px 90px;
  outline: none;
  line-height: 1.85;
  color: #162b25;
  font-size: 16px;
  word-break: break-word;
}

.paper-content :deep(p) {
  margin: 0 0 12px;
}

.onlyoffice-shell {
  position: relative;
  width: min(100%, 1680px);
  height: calc(100vh - 100px);
  margin: 0 auto;
  background: #fff;
  border: 1px solid #dce7e3;
  box-shadow: 0 14px 34px rgba(40, 70, 62, 0.1);
}

.assistant-resizer {
  width: 8px;
  height: 100%;
  padding: 0;
  border: none;
  border-left: 1px solid #d8e8e3;
  border-right: 1px solid #edf5f2;
  background: linear-gradient(180deg, #f2faf7, #deeee9);
  cursor: col-resize;
  position: relative;
}

.assistant-resizer::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  width: 2px;
  height: 42px;
  border-radius: 999px;
  background: #9acdc0;
  transform: translate(-50%, -50%);
  opacity: 0.75;
}

.assistant-resizer:hover::after {
  background: #008d72;
  opacity: 1;
}

.onlyoffice-editor {
  width: 100%;
  height: 100%;
}

.onlyoffice-diagnostic {
  position: absolute;
  left: 50%;
  bottom: 28px;
  z-index: 3;
  width: min(560px, calc(100% - 48px));
  transform: translateX(-50%);
  padding: 16px 18px;
  border: 1px solid #c9e5dc;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 14px 30px rgba(12, 83, 68, 0.16);
  color: #14332b;
}

.onlyoffice-diagnostic strong {
  display: block;
  margin-bottom: 6px;
  color: #006f5f;
}

.onlyoffice-diagnostic p {
  margin: 0;
  line-height: 1.7;
  font-size: 13px;
  color: #526c63;
}

.pdf-preview {
  width: min(960px, calc(100vw - 500px));
  height: calc(100vh - 150px);
  margin: 0 auto;
  background: #fff;
  border: 1px solid #dce7e3;
  box-shadow: 0 18px 48px rgba(40, 70, 62, 0.12);
}

.pdf-preview iframe {
  width: 100%;
  height: 100%;
  border: none;
}

.preview-empty {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #647873;
}

.ai-panel {
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-left: none;
  min-width: 0;
}

.ai-panel-section {
  padding: 22px 24px 18px;
  border-bottom: 1px solid #eef3f1;
}

.ai-panel-section h2 {
  margin: 0 0 18px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #12352e;
  font-size: 18px;
}

.ai-panel-section h2 svg {
  width: 18px;
  height: 18px;
  color: #008d72;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  border: 1px solid #e3ece8;
  border-radius: 8px;
  overflow: hidden;
  background: #f8fbfa;
}

.stats-grid div {
  min-width: 0;
  padding: 12px 6px;
  text-align: center;
  border-right: 1px solid #e3ece8;
}

.stats-grid div:last-child {
  border-right: none;
}

.stats-grid strong {
  display: block;
  color: #008d72;
  font-size: 16px;
}

.stats-grid span {
  display: block;
  margin-top: 4px;
  color: #7a8d88;
  font-size: 12px;
}

.summary-card {
  margin: 18px 20px;
  padding: 18px;
  border-radius: 8px;
  border: 1px solid #dcebe6;
  border-left: 4px solid #008d72;
  background: #fbfefd;
}

.summary-card strong {
  color: #153b32;
}

.summary-card p,
.assistant-placeholder p {
  margin: 8px 0 0;
  color: #314d45;
  line-height: 1.65;
}

.assistant-placeholder {
  margin: 0 20px;
  padding: 18px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #e8eeee;
  box-shadow: 0 8px 20px rgba(39, 70, 61, 0.06);
}

.assistant-input {
  margin-top: auto;
  padding: 20px;
  border-top: 1px solid #eef3f1;
  display: grid;
  gap: 10px;
}

.assistant-input label {
  color: #5d746e;
  font-size: 14px;
}

.assistant-input textarea {
  height: 88px;
  resize: none;
  border: 1px solid #dce8e4;
  border-radius: 8px;
  padding: 10px;
  color: #879791;
  background: #fafcfc;
}

.assistant-input button {
  height: 40px;
  border: none;
  border-radius: 8px;
  background: #cfe4dc;
  color: #6d807a;
  font-weight: 700;
}

@media (max-width: 980px) {
  .editor-workspace {
    grid-template-columns: 1fr;
  }

  .assistant-resizer {
    display: none;
  }

  .ai-panel {
    display: none;
  }

  .paper,
  .pdf-preview,
  .onlyoffice-shell {
    width: min(92vw, 820px);
  }

  .toolbar-actions {
    gap: 6px;
  }
}
</style>

<style>
body.resizing-assistant-panel {
  cursor: col-resize;
  user-select: none;
}
</style>
