<template>
  <StudioLayout>
    <div class="feature-page">
      <header class="feature-hero">
        <div>
          <span class="eyebrow">Knowledge Insights</span>
          <h1>知识洞察</h1>
          <p>面向文档库和知识库的运行看板，观察资料解析、索引覆盖、知识任务和检索可用性。</p>
        </div>
        <button class="primary-button" type="button" @click="refresh">
          <Refresh />
          刷新
        </button>
      </header>

      <section class="feature-grid three">
        <article v-for="card in cards" :key="card.label" class="feature-card">
          <component :is="card.icon" />
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
          <p>{{ card.desc }}</p>
        </article>
      </section>

      <section class="surface">
        <div class="surface-header">
          <div>
            <h2>文档索引明细</h2>
            <p>每条记录都来自当前用户的文件元数据，展示解析进度、摘要和关键词覆盖情况。</p>
          </div>
        </div>
        <div class="deliverable-table knowledge-table">
          <div class="deliverable-row head">
            <span>资料</span>
            <span>状态</span>
            <span>关键词</span>
            <span>进度</span>
          </div>
          <div v-for="file in files" :key="file.fileId || file.id" class="deliverable-row">
            <div>
              <strong>{{ displayFileName(file) }}</strong>
              <small>{{ displaySummary(file) || formatFileMeta(file) }}</small>
            </div>
            <em>{{ fileStatusLabel(file) }}</em>
            <span>{{ displayKeywords(file) }}</span>
            <button type="button" @click="goKnowledge">查看</button>
          </div>
          <div v-if="!files.length" class="empty-state">暂无知识洞察数据</div>
        </div>
      </section>

      <section class="surface audit-panel">
        <div class="surface-header compact">
          <div>
            <h2>优化建议</h2>
            <p>基于当前资料状态给出的知识库建设建议。</p>
          </div>
        </div>
        <div class="audit-list">
          <div v-for="item in suggestions" :key="item.title">
            <strong>{{ item.title }}</strong>
            <span>{{ item.detail }}</span>
          </div>
        </div>
      </section>
    </div>
  </StudioLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import StudioLayout from '../components/StudioLayout.vue'
import { workspaceApi } from '../api/workspace'
import { filterDeletingFiles } from '../utils/deletedFiles'
import { removeFileExtension, resolveFileTypeLabel } from '../utils/fileDisplay'
import { Collection, DataAnalysis, Files, Refresh } from '@element-plus/icons-vue'

const router = useRouter()
const files = ref([])
const todos = ref([])

const indexedFiles = computed(() => files.value.filter((item) => normalizedStatus(item) === 'INDEXED'))
const pendingFiles = computed(() => files.value.filter((item) => ['PENDING', 'INDEXING'].includes(normalizedStatus(item))))

const cards = computed(() => [
  { label: '文档总数', value: String(files.value.length), desc: '当前用户文档库中的资料数量', icon: Files },
  { label: '已索引资料', value: String(indexedFiles.value.length), desc: '可被 RAG 检索和 AI 阅读室引用的资料', icon: DataAnalysis },
  { label: '知识任务', value: String(todos.value.length), desc: '摘要、问答、关键词和引用检查任务', icon: Collection },
])

const suggestions = computed(() => {
  const items = []
  if (!files.value.length) {
    items.push({ title: '上传第一批资料', detail: '文档库为空，先上传资料才能建立知识库和进行问答。' })
  }
  if (pendingFiles.value.length) {
    items.push({ title: '等待解析完成', detail: `${pendingFiles.value.length} 份资料仍在解析或等待队列中，索引完成后召回覆盖会更稳定。` })
  }
  if (indexedFiles.value.length) {
    items.push({ title: '开始 RAG 验证', detail: '已索引资料可以进入 AI 阅读室提问，建议用 Judge.md 中的评估集验证召回率和精确率。' })
  }
  items.push({ title: '完善日志系统', detail: '建议记录每次查询召回的文件、chunk、分数、上下文窗口和 token 用量。' })
  return items
})

const normalizedStatus = (file) => String(file.understandStatus || file.status || '').toUpperCase()

const fileStatusLabel = (file) => ({
  PENDING: '待解析',
  INDEXING: '解析中',
  INDEXED: '已索引',
  FAILED: '解析失败',
}[normalizedStatus(file)] || '未解析')

const displaySummary = (file) => {
  const summary = String(file.summary || '').trim()
  return summary && summary !== '无' ? summary : ''
}

const displayKeywords = (file) => {
  const keywords = String(file.keywords || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(0, 3)
  return keywords.length ? keywords.join('、') : '-'
}

/**
 * 函数功能：展示不带后缀的文件名。
 */
const displayFileName = (file) => removeFileExtension(file.fileName || file.filename || file.originalName || '')

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

const formatFileMeta = (file) => `${file.fileType || resolveFileTypeLabel(file.fileName || file.filename)} / ${formatFileSize(file.fileSize)}`

const refresh = async () => {
  try {
    const [fileRes, todoRes] = await Promise.all([
      workspaceApi.listKnowledgeFiles(),
      workspaceApi.listKnowledgeTasks(),
    ])
    files.value = filterDeletingFiles(fileRes.data)
    todos.value = Array.isArray(todoRes.data) ? todoRes.data : []
  } catch (error) {
    console.warn('知识洞察数据加载失败', error)
    files.value = []
    todos.value = []
  }
}

const goKnowledge = () => {
  router.push('/knowledge')
}

onMounted(refresh)
</script>
