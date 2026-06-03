<template>
  <StudioLayout>
    <div class="feature-page">
      <header class="feature-hero">
        <div>
          <span class="eyebrow">Knowledge Map</span>
          <h1>知识图谱</h1>
          <p>
            展示文档库经过解析、摘要、关键词和向量索引后形成的知识结构，
            用于观察资料覆盖范围、主题分布和索引健康度。
          </p>
        </div>
        <button class="primary-button" type="button" @click="goKnowledge">
          <Collection />
          查看文档库
        </button>
      </header>

      <section class="factory-summary-grid">
        <article v-for="item in knowledgeRoles" :key="item.title" class="factory-tool compact">
          <component :is="item.icon" />
          <strong>{{ item.title }}</strong>
          <p>{{ item.desc }}</p>
        </article>
      </section>

      <section class="factory-workbench factory-template-workbench">
        <section class="surface template-builder">
          <div class="surface-header">
            <div>
              <h2>主题聚合</h2>
              <p>根据资料关键词和文件类型生成主题视图，帮助判断知识库覆盖是否均衡。</p>
            </div>
          </div>

          <div class="preset-list">
            <article v-for="topic in topics" :key="topic.name" class="preset-card">
              <div class="preset-head">
                <component :is="topic.icon" />
                <div>
                  <strong>{{ topic.name }}</strong>
                  <span>{{ topic.files }} 份资料</span>
                </div>
                <em>{{ topic.weight }}%</em>
              </div>
              <p>{{ topic.desc }}</p>
              <div class="template-pill-row">
                <span v-for="tag in topic.tags" :key="tag">{{ tag }}</span>
              </div>
            </article>
            <div v-if="!topics.length" class="empty-state">暂无主题数据，请先上传资料并等待解析完成。</div>
          </div>
        </section>

        <aside class="surface factory-rule-panel">
          <div class="surface-header compact">
            <div>
              <h2>索引规则</h2>
              <p>当前知识库建设遵循的处理边界。</p>
            </div>
          </div>

          <div class="constraint-list">
            <label v-for="rule in indexRules" :key="rule">
              <input type="checkbox" checked disabled />
              <span>{{ rule }}</span>
            </label>
          </div>
        </aside>
      </section>

      <section class="surface factory-handoff">
        <div class="surface-header">
          <div>
            <h2>知识入库链路</h2>
            <p>DocNexus 当前聚焦文档入库、资料理解、切片索引、问答检索和效果评估。</p>
          </div>
        </div>
        <div class="handoff-list">
          <div v-for="step in handoffSteps" :key="step.title">
            <span>{{ step.order }}</span>
            <strong>{{ step.title }}</strong>
            <p>{{ step.desc }}</p>
          </div>
        </div>
        <p class="factory-note">
          后续优化重点是提升解析质量、召回率、引用可追溯性和知识库评估，让资料更容易被检索、理解和复用。
        </p>
      </section>
    </div>
  </StudioLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import StudioLayout from '../components/StudioLayout.vue'
import { Collection, DataAnalysis, Files, Notebook, Reading, Search } from '@element-plus/icons-vue'
import { workspaceApi } from '../api/workspace'
import { filterDeletingFiles } from '../utils/deletedFiles'

const router = useRouter()
const files = ref([])

const knowledgeRoles = [
  {
    title: '文档入库',
    desc: '保留原始资料、文件元数据、解析状态和对象存储位置。',
    icon: Files,
  },
  {
    title: '资料理解',
    desc: '为每份资料生成摘要、关键词、质量评分和解析告警。',
    icon: Reading,
  },
  {
    title: '混合检索',
    desc: '使用向量召回、BM25 补充和 Rerank 组织可引用片段。',
    icon: Search,
  },
  {
    title: '知识评估',
    desc: '围绕召回率、精确率、上下文窗口和日志追踪持续优化。',
    icon: DataAnalysis,
  },
]

const topics = computed(() => {
  if (!files.value.length) return []
  const buckets = new Map()
  files.value.forEach((file) => {
    const tags = parseTags(file)
    const name = tags[0] || file.fileType || '未分类资料'
    const bucket = buckets.get(name) || { name, files: 0, tags: new Set(), icon: iconForFile(file) }
    bucket.files += 1
    tags.forEach((tag) => bucket.tags.add(tag))
    buckets.set(name, bucket)
  })
  return Array.from(buckets.values()).map((item) => ({
    ...item,
    weight: Math.round((item.files / files.value.length) * 100),
    tags: Array.from(item.tags).slice(0, 5),
    desc: `${item.name} 相关资料已进入文档库，可用于 AI 阅读室问答和知识任务。`,
  }))
})

const indexRules = [
  '只围绕用户上传资料构建文档库和知识库',
  '解析结果必须保留来源文件、片段编号和章节信息',
  'AI 回答优先引用已索引资料，无法确认时明确提示资料不足',
  'RAG 效果通过召回率、精确率和检索日志持续评估',
]

const handoffSteps = [
  {
    order: '01',
    title: '上传资料',
    desc: '用户上传 PDF、Word、PPT、Markdown 或纯文本，Java 保存元数据和对象路径。',
  },
  {
    order: '02',
    title: '解析摘要',
    desc: 'Python Agent 抽取正文，生成摘要、关键词、质量评分和结构化块。',
  },
  {
    order: '03',
    title: '切片入库',
    desc: '系统生成父子分片、向量和 payload，并写入 Qdrant 知识库。',
  },
  {
    order: '04',
    title: '问答检索',
    desc: 'AI 阅读室基于知识库召回片段，生成带来源的回答。',
  },
  {
    order: '05',
    title: '评估优化',
    desc: '通过 Judge.md 规划的指标和日志体系持续优化检索效果。',
  },
]

const parseTags = (file) => String(file.keywords || '')
  .split(',')
  .map((tag) => tag.trim())
  .filter((tag) => tag && tag !== '无')

const iconForFile = (file) => {
  const name = file.fileName || file.filename || ''
  const suffix = name.includes('.') ? name.split('.').pop().toLowerCase() : ''
  if (suffix === 'md' || suffix === 'markdown') return Notebook
  if (suffix === 'pdf' || suffix === 'doc' || suffix === 'docx' || suffix === 'ppt' || suffix === 'pptx') return Files
  return Collection
}

const loadFiles = async () => {
  try {
    const response = await workspaceApi.listKnowledgeFiles()
    files.value = filterDeletingFiles(response.data)
  } catch (error) {
    console.warn('知识图谱资料加载失败', error)
    files.value = []
  }
}

const goKnowledge = () => {
  router.push('/knowledge')
}

onMounted(loadFiles)
</script>
