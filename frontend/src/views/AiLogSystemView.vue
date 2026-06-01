<template>
  <StudioLayout>
    <div class="ai-log-page">
      <header class="feature-hero ai-log-hero">
        <div>
          <span class="eyebrow">AI Logs</span>
          <h1>AI 日志系统</h1>
          <p>
            记录当前用户每次 AI 调用的执行状态、召回资料、工具步骤、耗时和上下文窗口，
            用于追踪 RAG 问答、摘要、关键词提取与引用检查过程。
          </p>
        </div>
        <button v-if="!isAdminUser" class="primary-button" type="button" :disabled="loading" @click="loadLogs">
          <Refresh />
          {{ loading ? '刷新中' : '刷新日志' }}
        </button>
      </header>

      <section v-if="isAdminUser" class="surface admin-log-placeholder">
        <Warning />
        <div>
          <h2>admin 日志系统暂不设计</h2>
          <p>当前页面只面向普通用户展示自己的 AI 调用日志。管理员全局审计、跨用户检索和权限策略后续单独设计。</p>
        </div>
      </section>

      <template v-else>
        <section class="feature-grid three">
          <article v-for="card in statCards" :key="card.label" class="feature-card">
            <component :is="card.icon" />
            <span>{{ card.label }}</span>
            <strong>{{ card.value }}</strong>
            <p>{{ card.desc }}</p>
          </article>
        </section>

        <section class="ai-log-shell">
          <aside class="surface ai-log-filter-panel">
            <div class="surface-header compact">
              <div>
                <h2>日志筛选</h2>
                <p>只展示当前登录用户自己的日志。</p>
              </div>
            </div>

            <label class="log-field">
              <span>关键词</span>
              <input v-model.trim="filters.keyword" placeholder="搜索问题、答案、Trace ID" />
            </label>

            <label class="log-field">
              <span>状态</span>
              <select v-model="filters.status">
                <option value="all">全部状态</option>
                <option value="success">成功</option>
                <option value="failed">失败</option>
                <option value="action_required">待确认</option>
                <option value="cancelled">已取消</option>
              </select>
            </label>

            <label class="log-field">
              <span>日志类型</span>
              <select v-model="filters.type">
                <option value="all">全部类型</option>
                <option value="rag">RAG 检索</option>
                <option value="summary">资料摘要</option>
                <option value="keywords">关键词提取</option>
                <option value="review">引用检查</option>
              </select>
            </label>

            <div class="log-scope-card">
              <User />
              <div>
                <strong>{{ currentUserLabel }}</strong>
                <span>日志可见范围：仅本人</span>
              </div>
            </div>
          </aside>

          <section class="surface ai-log-list-panel">
            <div class="surface-header compact">
              <div>
                <h2>调用日志</h2>
                <p>每条日志对应一次 Python Agent 或 RAG 执行快照。</p>
              </div>
              <span class="log-count">{{ filteredLogs.length }} 条</span>
            </div>

            <div class="ai-log-list">
              <article
                v-for="log in filteredLogs"
                :key="log.traceId"
                class="ai-log-card"
                :class="{ selected: selectedLog?.traceId === log.traceId }"
                @click="selectLog(log)"
              >
                <div class="log-card-top">
                  <span :class="['log-status', `status-${log.status}`]">{{ statusLabel(log.status) }}</span>
                  <em>{{ formatTime(log.updatedAt || log.startedAt) }}</em>
                </div>
                <h3>{{ log.title }}</h3>
                <p>{{ log.answer || '暂无最终回答，查看右侧详情了解执行步骤。' }}</p>
                <div class="log-card-meta">
                  <span><Timer />{{ log.durationMs }}ms</span>
                  <span><Search />{{ log.sourceCount }} 个召回片段</span>
                  <span><Coin />{{ displayTokenUsage(log) }}</span>
                </div>
              </article>

              <div v-if="!filteredLogs.length" class="empty-state">
                {{ loading ? '正在加载 AI 日志' : '暂无符合条件的 AI 日志' }}
              </div>
            </div>
          </section>

          <section class="surface ai-log-detail-panel">
            <div class="surface-header compact">
              <div>
                <h2>日志详情</h2>
                <p>查看召回资料、工具步骤和上下文窗口。</p>
              </div>
            </div>

            <template v-if="selectedLog">
              <div class="trace-box">
                <span>Trace ID</span>
                <strong>{{ selectedLog.traceId }}</strong>
              </div>

              <div class="detail-section">
                <h3>召回资料</h3>
                <div class="source-list">
                  <div v-for="source in selectedLog.sources" :key="source.key" class="source-item">
                    <strong>{{ source.fileName }}</strong>
                    <span>{{ source.chunkLabel }}</span>
                    <p>{{ source.content }}</p>
                    <em>score {{ source.score }}</em>
                  </div>
                  <div v-if="!selectedLog.sources.length" class="empty-state small">本次日志暂无召回资料</div>
                </div>
              </div>

              <div class="detail-section">
                <h3>执行步骤</h3>
                <div class="step-timeline">
                  <div v-for="step in selectedLog.steps" :key="step.key" class="step-item">
                    <span :class="['step-dot', step.status]" />
                    <div>
                      <strong>{{ step.title }}</strong>
                      <p>{{ step.detail }}</p>
                    </div>
                    <em>{{ step.toolName }}</em>
                  </div>
                  <div v-if="!selectedLog.steps.length" class="empty-state small">本次日志暂无结构化步骤</div>
                </div>
              </div>

              <div class="detail-section">
                <h3>上下文与消耗</h3>
                <div class="context-grid">
                  <div>
                    <span>TopK</span>
                    <strong>{{ selectedLog.topK }}</strong>
                  </div>
                  <div>
                    <span>上下文片段</span>
                    <strong>{{ selectedLog.sourceCount }}</strong>
                  </div>
                  <div>
                    <span>Token 消耗</span>
                    <strong>{{ displayTokenUsage(selectedLog) }}</strong>
                  </div>
                  <div>
                    <span>知识库</span>
                    <strong>{{ selectedLog.knowledgeBaseId }}</strong>
                  </div>
                </div>
              </div>
            </template>

            <div v-else class="empty-state">请选择一条 AI 日志查看详情</div>
          </section>
        </section>
      </template>
    </div>
  </StudioLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import StudioLayout from '../components/StudioLayout.vue'
import { agentLogApi } from '../api/agentLog'
import { STORAGE_KEYS } from '../constants'
import { Coin, DataAnalysis, DocumentChecked, Refresh, Search, Timer, User, Warning } from '@element-plus/icons-vue'

const loading = ref(false)
const logs = ref([])
const selectedTraceId = ref('')
const filters = reactive({
  keyword: '',
  status: 'all',
  type: 'all',
})

const currentUser = computed(() => {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEYS.USER_INFO) || '{}')
  } catch {
    return {}
  }
})

const currentUserId = computed(() => String(currentUser.value.id || currentUser.value.userId || localStorage.getItem('userId') || ''))
const currentUsername = computed(() => String(currentUser.value.username || currentUser.value.name || ''))
const currentRole = computed(() => String(currentUser.value.role || currentUser.value.userRole || '').toLowerCase())
const isAdminUser = computed(() => currentUsername.value.toLowerCase() === 'admin' || currentRole.value === 'admin')
const currentUserLabel = computed(() => currentUser.value.displayName || currentUsername.value || `用户 ${currentUserId.value || '-'}`)

const ownLogs = computed(() => {
  if (isAdminUser.value) return []
  return logs.value.filter((log) => {
    // 后端已按 JWT userId 查询；这里对带 userId 的日志再做一次前端过滤。
    if (log.userId === undefined || log.userId === null || log.userId === '') return true
    return String(log.userId) === currentUserId.value
  })
})

const filteredLogs = computed(() => {
  const keyword = filters.keyword.toLowerCase()
  return ownLogs.value.filter((log) => {
    const matchesKeyword = !keyword || [
      log.traceId,
      log.title,
      log.answer,
      log.knowledgeBaseId,
    ].some((value) => String(value || '').toLowerCase().includes(keyword))
    const matchesStatus = filters.status === 'all' || log.status === filters.status
    const matchesType = filters.type === 'all' || log.type === filters.type
    return matchesKeyword && matchesStatus && matchesType
  })
})

const selectedLog = computed(() => filteredLogs.value.find((log) => log.traceId === selectedTraceId.value) || filteredLogs.value[0] || null)

const statCards = computed(() => {
  const total = ownLogs.value.length
  const successCount = ownLogs.value.filter((log) => log.status === 'success').length
  const sourceCount = ownLogs.value.reduce((sum, log) => sum + log.sourceCount, 0)
  const tokenCount = ownLogs.value.reduce((sum, log) => sum + log.tokenUsage.total, 0)
  return [
    { label: '日志总数', value: String(total), desc: '当前用户自己的 AI 调用记录', icon: DocumentChecked },
    { label: '成功调用', value: String(successCount), desc: '状态为 success 的 Agent 执行快照', icon: DataAnalysis },
    { label: '召回片段', value: String(sourceCount), desc: '所有日志累计召回的资料片段', icon: Search },
    { label: 'Token 消耗', value: tokenCount ? String(tokenCount) : '-', desc: '后端返回 tokenUsage 时自动汇总', icon: Coin },
  ]
})

const normalizeLog = (raw) => {
  const context = raw.context || {}
  const contextWindow = raw.contextWindow || raw.context_window || context.contextWindow || {}
  const sources = normalizeSources(raw.sources || contextWindow.sources || [])
  const steps = normalizeSteps(raw.plan || [], raw.results || [])
  const tokenUsage = normalizeTokenUsage(raw.tokenUsage || raw.usage || contextWindow.tokenUsage || {})
  return {
    traceId: String(raw.traceId || raw.trace_id || raw.id || ''),
    userId: raw.userId ?? raw.user_id ?? context.userId,
    title: String(raw.task || context.title || context.instruction || 'AI 调用日志'),
    type: resolveLogType(raw, context),
    status: normalizeStatus(raw.status),
    answer: String(raw.answer || raw.result || '').slice(0, 220),
    durationMs: Number(raw.durationMs || raw.duration_ms || 0),
    updatedAt: raw.updatedAt || raw.updated_at || raw.finishedAt || raw.startedAt,
    startedAt: raw.startedAt,
    sources,
    sourceCount: sources.length,
    steps,
    topK: raw.topK || raw.top_k || context.topK || contextWindow.topK || '-',
    tokenUsage,
    knowledgeBaseId: raw.knowledgeBaseId || raw.knowledge_base_id || context.knowledgeBaseId || 'default',
  }
}

const normalizeSources = (sources) => {
  if (!Array.isArray(sources)) return []
  return sources.slice(0, 8).map((source, index) => ({
    key: `${source.fileId || source.fileName || 'source'}-${source.chunkNo || index}`,
    fileName: source.fileName || source.filename || source.documentName || '未知资料',
    chunkLabel: source.chunkNo || source.chunkId ? `片段 ${source.chunkNo || source.chunkId}` : `片段 ${index + 1}`,
    content: String(source.content || source.text || source.chunkText || '').slice(0, 180) || '暂无片段内容',
    score: Number(source.score || source.similarity || 0).toFixed(3),
  }))
}

const normalizeSteps = (plan, results) => {
  const resultMap = new Map((Array.isArray(results) ? results : []).map((result) => [String(result.stepId || result.step_id || ''), result]))
  if (Array.isArray(plan) && plan.length) {
    return plan.map((step, index) => {
      const stepId = String(step.id || `step-${index}`)
      const result = resultMap.get(stepId) || {}
      return {
        key: stepId,
        title: step.description || `步骤 ${index + 1}`,
        detail: result.message || step.reasoning || '等待执行结果',
        toolName: step.toolName || step.tool_name || result.toolName || 'agent',
        status: normalizeStatus(result.status || step.status || 'planned'),
      }
    })
  }
  return (Array.isArray(results) ? results : []).map((result, index) => ({
    key: String(result.stepId || result.step_id || `result-${index}`),
    title: result.message || `执行结果 ${index + 1}`,
    detail: result.message || '工具执行完成',
    toolName: result.toolName || result.tool_name || 'agent',
    status: normalizeStatus(result.status),
  }))
}

const normalizeTokenUsage = (usage) => {
  const prompt = Number(usage.promptTokens || usage.prompt_tokens || 0)
  const completion = Number(usage.completionTokens || usage.completion_tokens || 0)
  const total = Number(usage.totalTokens || usage.total_tokens || prompt + completion || 0)
  return { prompt, completion, total }
}

const normalizeStatus = (status) => {
  const normalized = String(status || '').toLowerCase()
  if (['success', 'failed', 'error', 'action_required', 'cancelled'].includes(normalized)) {
    return normalized === 'error' ? 'failed' : normalized
  }
  return normalized || 'planned'
}

const resolveLogType = (raw, context) => {
  const value = String(context.todoType || context.type || raw.type || raw.mode || '').toLowerCase()
  if (['summary', 'keywords', 'review'].includes(value)) return value
  if (value.includes('rag') || value.includes('chat')) return 'rag'
  return 'rag'
}

const statusLabel = (status) => ({
  success: '成功',
  failed: '失败',
  action_required: '待确认',
  cancelled: '已取消',
  planned: '已计划',
}[status] || status)

const displayTokenUsage = (log) => {
  return log.tokenUsage.total ? `${log.tokenUsage.total}` : '-'
}

const formatTime = (value) => {
  if (!value) return '未知时间'
  const date = new Date(Number(value) || value)
  if (Number.isNaN(date.getTime())) return '未知时间'
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${month}-${day} ${hour}:${minute}`
}

const selectLog = (log) => {
  selectedTraceId.value = log.traceId
}

const loadLogs = async () => {
  if (isAdminUser.value) {
    logs.value = []
    return
  }
  loading.value = true
  try {
    const response = await agentLogApi.listTasks()
    const list = Array.isArray(response.data) ? response.data : []
    logs.value = list.map(normalizeLog).filter((log) => log.traceId)
    selectedTraceId.value = logs.value[0]?.traceId || ''
  } catch (error) {
    console.warn('AI 日志加载失败', error)
    logs.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadLogs)
</script>

<style scoped>
.ai-log-page {
  min-height: 100%;
}

.ai-log-hero p {
  max-width: 860px;
}

.admin-log-placeholder {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 28px;
}

.admin-log-placeholder svg {
  width: 34px;
  height: 34px;
  color: #b45309;
}

.admin-log-placeholder h2 {
  font-size: 22px;
}

.admin-log-placeholder p {
  margin-top: 8px;
  color: #64748b;
  line-height: 1.7;
}

.ai-log-shell {
  display: grid;
  grid-template-columns: 280px minmax(360px, 0.9fr) minmax(420px, 1.1fr);
  gap: 16px;
  align-items: start;
}

.ai-log-filter-panel,
.ai-log-list-panel,
.ai-log-detail-panel {
  min-width: 0;
}

.log-field {
  display: block;
  margin-top: 14px;
}

.log-field span {
  display: block;
  margin-bottom: 7px;
  color: #475569;
  font-size: 13px;
  font-weight: 800;
}

.log-field input,
.log-field select {
  width: 100%;
  height: 40px;
  border: 1px solid #d7dee9;
  border-radius: 8px;
  background: #ffffff;
  color: #172033;
  padding: 0 11px;
  outline: none;
}

.log-field input:focus,
.log-field select:focus {
  border-color: #047857;
}

.log-scope-card {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 18px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #eff6ff;
  padding: 12px;
}

.log-scope-card svg {
  width: 22px;
  height: 22px;
  color: #2563eb;
}

.log-scope-card strong,
.log-scope-card span {
  display: block;
}

.log-scope-card span {
  margin-top: 2px;
  color: #64748b;
  font-size: 12px;
}

.log-count {
  border-radius: 999px;
  background: #e2e8f0;
  color: #334155;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 800;
}

.ai-log-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 660px;
  overflow: auto;
  padding-right: 3px;
}

.ai-log-card {
  cursor: pointer;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
  padding: 13px;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.ai-log-card:hover,
.ai-log-card.selected {
  border-color: #047857;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
}

.log-card-top,
.log-card-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.log-card-top {
  justify-content: space-between;
}

.log-card-top em {
  color: #94a3b8;
  font-size: 12px;
  font-style: normal;
}

.log-status {
  border-radius: 999px;
  background: #e2e8f0;
  color: #475569;
  padding: 4px 8px;
  font-size: 12px;
  font-weight: 800;
}

.status-success {
  background: #dcfce7;
  color: #047857;
}

.status-failed {
  background: #fee2e2;
  color: #b91c1c;
}

.status-action_required {
  background: #fef3c7;
  color: #a16207;
}

.ai-log-card h3 {
  margin-top: 10px;
  font-size: 15px;
  line-height: 1.45;
}

.ai-log-card p {
  margin-top: 8px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.55;
}

.log-card-meta {
  flex-wrap: wrap;
  margin-top: 10px;
  color: #475569;
  font-size: 12px;
}

.log-card-meta span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.log-card-meta svg {
  width: 14px;
  height: 14px;
  color: #047857;
}

.trace-box {
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  background: #f8fafc;
  padding: 12px;
}

.trace-box span,
.trace-box strong {
  display: block;
}

.trace-box span {
  color: #64748b;
  font-size: 12px;
}

.trace-box strong {
  margin-top: 4px;
  word-break: break-all;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
}

.detail-section {
  margin-top: 18px;
}

.detail-section h3 {
  margin-bottom: 10px;
  font-size: 16px;
}

.source-list,
.step-timeline {
  display: flex;
  flex-direction: column;
  gap: 9px;
}

.source-item,
.step-item {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #ffffff;
  padding: 11px;
}

.source-item strong,
.source-item span,
.source-item em {
  display: block;
}

.source-item span,
.source-item em {
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
  font-style: normal;
}

.source-item p {
  margin-top: 8px;
  color: #334155;
  font-size: 13px;
  line-height: 1.55;
}

.step-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 10px;
  align-items: start;
}

.step-dot {
  width: 10px;
  height: 10px;
  margin-top: 5px;
  border-radius: 999px;
  background: #94a3b8;
}

.step-dot.success {
  background: #047857;
}

.step-dot.failed {
  background: #b91c1c;
}

.step-dot.action_required {
  background: #a16207;
}

.step-item strong {
  display: block;
  font-size: 14px;
}

.step-item p {
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.step-item em {
  color: #64748b;
  font-size: 12px;
  font-style: normal;
  white-space: nowrap;
}

.context-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.context-grid div {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
  padding: 11px;
}

.context-grid span,
.context-grid strong {
  display: block;
}

.context-grid span {
  color: #64748b;
  font-size: 12px;
}

.context-grid strong {
  margin-top: 4px;
  color: #172033;
  font-size: 15px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-state.small {
  min-height: 72px;
}

@media (max-width: 1280px) {
  .ai-log-shell {
    grid-template-columns: 1fr;
  }

  .ai-log-list {
    max-height: none;
  }
}
</style>
