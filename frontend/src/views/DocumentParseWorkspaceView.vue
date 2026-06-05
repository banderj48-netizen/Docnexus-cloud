<template>
  <StudioLayout>
    <main class="parse-page">
      <section class="stage-shell" aria-label="解析阶段">
        <article
          v-for="stage in stages"
          :key="stage.index"
          class="stage-card"
          :class="{ active: stage.active, clickable: stage.clickable, disabled: stage.disabled }"
          @click="handleStageClick(stage)"
        >
          <strong>{{ stage.index }}</strong>
          <div>
            <h2>{{ stage.title }}</h2>
            <p>{{ stage.desc }}</p>
          </div>
          <span>{{ stage.status }}</span>
        </article>
      </section>

      <section class="workspace-title">
        <button class="back-button" type="button" @click="goBack">
          <Back />
          返回文档库
        </button>
        <div>
          <span>{{ viewMeta.englishTitle }}</span>
          <h1>{{ viewMeta.title }}</h1>
          <p>{{ currentFile.displayName }} · {{ currentFile.typeLabel }}</p>
        </div>
        <p class="workspace-tip">{{ viewMeta.description }}</p>
      </section>

      <template v-if="currentView === 'config'">
      <section class="pipeline-card parent">
        <div class="pipeline-heading">
          <div>
            <span>Answer Context Pipeline</span>
            <h2>父块流水线</h2>
          </div>
          <p>决定回答阶段看到的父块边界，优先保证结构完整、引用稳定和上下文连续。</p>
        </div>

        <div class="config-box">
          <h3>当前配置</h3>
          <div class="current-row single">
            <article v-for="item in parentCurrentFlow" :key="item.title" class="flow-item">
              <strong>{{ item.order }}</strong>
              <div>
                <h4>{{ item.title }}</h4>
                <p>{{ item.desc }}</p>
              </div>
              <div class="move-actions">
                <button type="button">上移</button>
                <button type="button">下移</button>
              </div>
            </article>
          </div>
        </div>

        <div class="option-grid">
          <button
            v-for="item in parentOptions"
            :key="item.title"
            class="option-card"
            :class="{ selected: item.selected }"
            type="button"
          >
            <span>{{ item.selected ? '已选中' : '点击添加' }}</span>
            <strong>{{ item.title }}</strong>
            <small>{{ item.desc }}</small>
            <CircleCheck v-if="item.selected" />
          </button>
        </div>

        <div class="final-box">
          <h3>父块流水线最终提交顺序</h3>
          <div class="final-pills">
            <span v-for="item in parentSelected" :key="item">{{ item }}</span>
          </div>
        </div>
      </section>

      <section class="pipeline-card child">
        <div class="pipeline-heading">
          <div>
            <span>Retrieval Recall Pipeline</span>
            <h2>子块流水线</h2>
          </div>
          <p>决定检索召回使用的子块边界，优先保证召回精度、主题聚合和证据可追溯。</p>
        </div>

        <div class="config-box">
          <h3>当前配置</h3>
          <div class="current-row double">
            <template v-for="(item, index) in childCurrentFlow" :key="item.title">
              <article class="flow-item">
                <strong>{{ item.order }}</strong>
                <div>
                  <h4>{{ item.title }}</h4>
                  <p>{{ item.desc }}</p>
                </div>
                <div class="move-actions">
                  <button type="button">上移</button>
                  <button type="button">下移</button>
                </div>
              </article>
              <b v-if="index < childCurrentFlow.length - 1" class="flow-arrow">-></b>
            </template>
          </div>
        </div>

        <div class="option-grid">
          <button
            v-for="item in childOptions"
            :key="item.title"
            class="option-card"
            :class="{ selected: item.selected }"
            type="button"
          >
            <span>{{ item.selected ? '已选中' : '点击添加' }}</span>
            <strong>{{ item.title }}</strong>
            <small>{{ item.desc }}</small>
            <CircleCheck v-if="item.selected" />
          </button>
        </div>

        <div class="final-box">
          <h3>子块流水线最终提交顺序</h3>
          <div class="final-pills">
            <template v-for="(item, index) in childSelected" :key="item">
              <span>{{ item }}</span>
              <b v-if="index < childSelected.length - 1">-></b>
            </template>
          </div>
        </div>
      </section>

      <section class="trace-summary">
        <article v-for="item in traceSummary" :key="item.label">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </article>
      </section>
      </template>

      <template v-else>
        <section class="chunk-panel">
          <div class="chunk-actions">
            <span class="count-pill">37 条</span>
            <strong>任务 2047993719885423063 · 37 条</strong>
            <div>
              <button class="active" type="button">按父块分组</button>
              <button type="button">平铺列表</button>
              <button type="button">展开全部</button>
              <button type="button">折叠全部</button>
            </div>
          </div>

          <div class="chunk-stats">
            <article v-for="stat in chunkStats" :key="stat.label">
              <span>{{ stat.label }}</span>
              <strong>{{ stat.value }}</strong>
            </article>
          </div>

          <article v-for="parent in chunkGroups" :key="parent.id" class="parent-block">
            <header>
              <div>
                <h2>{{ parent.title }}</h2>
                <p>{{ parent.path }}</p>
              </div>
              <div class="parent-actions">
                <button type="button">查看父块上下文</button>
                <button type="button">折叠子块</button>
                <span>子块 {{ parent.childText }}</span>
                <span>子块范围 {{ parent.range }}</span>
              </div>
            </header>
            <div class="chunk-token-row">
              <article v-for="child in parent.children" :key="child.id">
                <strong>#{{ child.index }}</strong>
                <span>{{ child.token }} Token</span>
              </article>
            </div>
            <div class="chunk-table">
              <div class="chunk-table-head">
                <span>Chunk</span>
                <span>章节 / 标识</span>
                <span>来源 / 状态</span>
                <span>字符</span>
                <span>Token</span>
                <span>内容预览</span>
              </div>
              <div v-for="child in parent.children" :key="child.id" class="chunk-table-row">
                <div>
                  <strong>子块 C#{{ child.index }}</strong>
                  <p>{{ child.id }}</p>
                  <a href="#">父块 P#{{ parent.id }} · 同父第 {{ child.index }}/{{ parent.children.length }} 子块</a>
                </div>
                <div>
                  <strong>{{ child.title }}</strong>
                  <p>{{ parent.path }}</p>
                </div>
                <div class="status-tags">
                  <span>原文切块</span>
                  <span>向量化成功</span>
                </div>
                <strong>{{ child.chars }}</strong>
                <strong>{{ child.token }}</strong>
                <p class="preview-text">{{ child.preview }}</p>
              </div>
            </div>
          </article>
        </section>
      </template>

      <button class="floating-log-button" type="button" @click="openTaskDrawer">
        查看任务记录
        <span>{{ taskLogs.length }} 条</span>
      </button>

      <div v-if="taskDrawerVisible" class="drawer-mask" @click.self="closeTaskDrawer">
        <aside class="task-drawer">
          <header>
            <div>
              <h2>任务执行详情</h2>
              <p>任务 2068728516320436634 · 构建索引</p>
            </div>
            <button type="button" @click="closeTaskDrawer">×</button>
          </header>
          <div class="status-row">
            <span>当前状态 <b>{{ buildComplete ? '成功' : '执行中' }}</b></span>
            <span>索引状态 <b>{{ buildComplete ? '构建成功' : '构建中' }}</b></span>
          </div>
          <ol class="log-timeline">
            <li v-for="log in taskLogs" :key="log.id">
              <strong>{{ log.title }} <span>{{ log.time }}</span></strong>
              <p>{{ log.message }}</p>
              <code>{{ log.payload }}</code>
            </li>
          </ol>
        </aside>
      </div>
    </main>
  </StudioLayout>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Back, CircleCheck } from '@element-plus/icons-vue'
import StudioLayout from '../components/StudioLayout.vue'
import { removeFileExtension } from '../utils/fileDisplay'

const router = useRouter()

/**
 * 读取文档库跳转时写入的文件上下文，保证演示页展示当前点击的文档名。
 */
const readPreviewFile = () => {
  try {
    return JSON.parse(sessionStorage.getItem('docnexusParsePreviewFile') || '{}')
  } catch {
    return {}
  }
}

const previewFile = readPreviewFile()

const currentFile = computed(() => ({
  name: previewFile.name || '企业知识库建设方案.docx',
  displayName: removeFileExtension(previewFile.name || '企业知识库建设方案.docx'),
  typeLabel: previewFile.typeLabel || 'WORD',
}))

const traceId = 'trace_20260605_parse_demo'
const currentView = ref('config')
const buildStatus = ref('RUNNING')
const taskDrawerVisible = ref(false)
const taskLogs = ref([])
const timers = []

const buildComplete = computed(() => buildStatus.value === 'SUCCESS')

const viewMeta = computed(() => {
  if (currentView.value === 'chunks') {
    return {
      englishTitle: 'Step 3',
      title: '验证 Chunk 结果',
      description: '在这里检查父子分块结构、分页浏览内容，并抽样验证切块是否符合预期。',
    }
  }
  return {
    englishTitle: 'Adjustment Workspace',
    title: '双流水线调整',
    description: '配置父块与子块流水线顺序。',
  }
})

const stages = computed(() => [
  {
    index: '01',
    key: 'config',
    title: '配置策略',
    desc: '推荐双流水线调整',
    status: '已确认',
    active: currentView.value === 'config' && buildComplete.value,
    clickable: true,
  },
  {
    index: '02',
    key: 'build',
    title: '确认并构建',
    desc: '确认方案并执行索引',
    status: buildComplete.value ? '已完成' : '执行中',
    active: !buildComplete.value,
    clickable: false,
  },
  {
    index: '03',
    key: 'chunks',
    title: '验证 Chunk 结果',
    desc: '检查分块结果与分页',
    status: buildComplete.value ? '37 条' : '等待构建',
    active: currentView.value === 'chunks',
    clickable: buildComplete.value,
    disabled: !buildComplete.value,
  },
  {
    index: '04',
    key: 'logs',
    title: '查看任务记录',
    desc: '复盘日志与时间线',
    status: `${taskLogs.value.length} 条日志`,
    clickable: true,
  },
])

const parentCurrentFlow = [
  { order: '01', title: '基于文档结构切块', desc: '优先保留标题和章节边界' },
]

const childCurrentFlow = [
  { order: '01', title: '语义分块', desc: '优化主题边界和段落完整性' },
  { order: '02', title: '递归分块', desc: '对超长内容继续裁剪兜底' },
]

const parentOptions = [
  { title: '基于文档结构切块', desc: '优先保留标题和章节边界', selected: true },
  { title: '递归分块', desc: '对超长内容继续裁剪兜底' },
  { title: '语义分块', desc: '优化主题边界和段落完整性' },
  { title: '大模型智能切块', desc: '处理复杂内容和低质量文本' },
]

const childOptions = [
  { title: '基于文档结构切块', desc: '优先保留标题和章节边界' },
  { title: '递归分块', desc: '对超长内容继续裁剪兜底', selected: true },
  { title: '语义分块', desc: '优化主题边界和段落完整性', selected: true },
  { title: '大模型智能切块', desc: '处理复杂内容和低质量文本' },
]

const parentSelected = ['基于文档结构切块']
const childSelected = ['语义分块', '递归分块']

const traceSummary = computed(() => [
  { label: '父块候选', value: '4 个策略' },
  { label: '子块候选', value: '4 个策略' },
  { label: '预计 Chunk', value: '106 条' },
  { label: 'Harness 状态', value: buildComplete.value ? '已完成' : '构建中' },
])

const chunkStats = [
  { label: '父块数', value: '19' },
  { label: '总片段', value: '37' },
  { label: '向量可用', value: '20' },
  { label: '待处理', value: '0' },
  { label: '平均 Token', value: '97' },
]

const chunkGroups = [
  {
    id: '1',
    title: '父块 P#1',
    path: '核心业务系统故障应急响应预案',
    childText: '1/1',
    range: 'C#1 - C#1',
    children: [
      {
        id: '2047993719885423069',
        index: '1',
        title: '核心业务系统故障应急响应预案',
        chars: 208,
        token: 107,
        preview: '# 核心业务系统故障应急响应预案 > 文档密...',
      },
    ],
  },
  {
    id: '2',
    title: '父块 P#2',
    path: '核心业务系统故障应急响应预案 > 一、编制目的与适用范围',
    childText: '2/2',
    range: 'C#2 - C#3',
    children: [
      {
        id: '2047993719885423070',
        index: '2',
        title: '一、编制目的',
        chars: 392,
        token: 206,
        preview: '用于规范核心业务系统故障时的响应流程、职责边界和恢复顺序...',
      },
      {
        id: '2047993719885423071',
        index: '3',
        title: '二、适用范围',
        chars: 148,
        token: 71,
        preview: '适用于生产环境、业务中台、网关及相关数据库异常场景...',
      },
    ],
  },
]

const logPlan = [
  {
    id: 'log_1',
    title: '切块执行 · 开始',
    time: '2026/04/16 20:26:32',
    message: '索引构建任务已创建，等待异步执行。',
    payload: '{"planId":2068728516320436628,"strategySnapshot":"PARENT:1;CHILD:3,2"}',
  },
  {
    id: 'log_2',
    title: '切块执行 · 开始',
    time: '2026/04/16 20:26:32',
    message: '开始执行切块流水线。',
    payload: '{"strategySnapshot":"PARENT:1;CHILD:3,2"}',
  },
  {
    id: 'log_3',
    title: '切块执行 · 完成',
    time: '2026/04/16 20:26:32',
    message: '切块执行完成。',
    payload: '{"childCount":72,"parentCount":67}',
  },
  {
    id: 'log_4',
    title: '切块后处理 · 完成',
    time: '2026/04/16 20:26:32',
    message: '切块后处理完成。',
    payload: '{"childCount":72,"parentCount":67}',
  },
  {
    id: 'log_5',
    title: '向量化 · 开始',
    time: '2026/04/16 20:26:32',
    message: '开始执行向量化。',
    payload: '{"chunkCount":72,"embeddingBatchSize":10,"vectorStoreType":"PGVector"}',
  },
  {
    id: 'log_6',
    title: '向量化 · 完成',
    time: '2026/04/16 20:26:33',
    message: '向量写入完成，等待索引可用性校验。',
    payload: '{"successCount":72,"failedCount":0}',
  },
  {
    id: 'log_7',
    title: '索引构建 · 完成',
    time: '2026/04/16 20:26:34',
    message: '索引构建成功，已进入 Chunk 验证阶段。',
    payload: '{"status":"SUCCESS","availableChunkCount":37}',
  },
]

/**
 * 根据阶段标签处理页面切换；验证 Chunk 必须等待构建完成。
 */
const handleStageClick = (stage) => {
  if (stage.key === 'logs') {
    openTaskDrawer()
    return
  }
  if (stage.key === 'chunks') {
    if (!buildComplete.value) {
      ElMessage.warning('索引构建完成后才能查看 Chunk 结果')
      return
    }
    currentView.value = 'chunks'
    return
  }
  if (stage.key === 'config') {
    currentView.value = 'config'
  }
}

/**
 * 打开任务记录抽屉，任务记录可在任何阶段查看。
 */
const openTaskDrawer = () => {
  taskDrawerVisible.value = true
}

/**
 * 关闭任务记录抽屉。
 */
const closeTaskDrawer = () => {
  taskDrawerVisible.value = false
}

/**
 * 前端写死模拟任务进度：日志逐条追加，构建完成后自动进入 Chunk 验证页。
 */
const startMockBuild = () => {
  taskLogs.value = [logPlan[0]]
  logPlan.slice(1).forEach((log, index) => {
    const timer = window.setTimeout(() => {
      taskLogs.value = [...taskLogs.value, log]
    }, (index + 1) * 650)
    timers.push(timer)
  })
  const finishTimer = window.setTimeout(() => {
    buildStatus.value = 'SUCCESS'
    currentView.value = 'chunks'
  }, 4400)
  timers.push(finishTimer)
}

onMounted(() => {
  startMockBuild()
})

onUnmounted(() => {
  timers.forEach((timer) => window.clearTimeout(timer))
})

/**
 * 返回文档库页面。
 */
const goBack = () => {
  router.push('/knowledge')
}
</script>

<style scoped>
.parse-page {
  min-height: calc(100vh - 72px);
  padding: 18px 28px 40px;
  background:
    linear-gradient(135deg, rgba(0, 141, 114, 0.08), rgba(255, 255, 255, 0.72) 32%),
    #f5faf7;
  color: #10251f;
}

.stage-shell,
.workspace-title,
.pipeline-card,
.trace-summary {
  max-width: 1660px;
  margin-left: auto;
  margin-right: auto;
}

.stage-shell {
  padding: 10px;
  border: 1px solid #d5ebe5;
  border-radius: 8px;
  background: rgba(232, 246, 241, 0.92);
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  box-shadow: 0 12px 28px rgba(0, 141, 114, 0.08);
}

.stage-card {
  min-height: 66px;
  padding: 10px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.86);
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  transition: border-color 0.18s ease, background 0.18s ease, transform 0.18s ease;
}

.stage-card.clickable {
  cursor: pointer;
}

.stage-card.clickable:hover {
  transform: translateY(-1px);
  border-color: #9bd2c4;
}

.stage-card.disabled {
  cursor: not-allowed;
  opacity: 0.66;
}

.stage-card.active {
  border-color: #008d72;
  background: linear-gradient(135deg, #e1f6ef, #fff);
  box-shadow: inset 0 0 0 1px rgba(0, 141, 114, 0.18);
}

.stage-card > strong {
  width: 38px;
  height: 38px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: #e7eee9;
  color: #687a75;
  font-weight: 900;
}

.stage-card.active > strong {
  background: #cceee4;
  color: #00765f;
}

.stage-card h2 {
  margin: 0;
  color: #17342d;
  font-size: 14px;
  letter-spacing: 0;
}

.stage-card p {
  margin: 4px 0 0;
  color: #657872;
  font-size: 12px;
}

.stage-card > span {
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  background: #edf3f0;
  color: #557069;
  font-size: 12px;
  font-weight: 800;
}

.workspace-title {
  margin-top: 18px;
  min-height: 112px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) 460px;
  gap: 20px;
  align-items: center;
}

.back-button {
  height: 38px;
  border: 1px solid #cfe7df;
  border-radius: 8px;
  background: #fff;
  color: #00765f;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 0 12px;
  font-weight: 800;
  cursor: pointer;
}

.back-button svg {
  width: 16px;
  height: 16px;
}

.workspace-title span,
.pipeline-heading span {
  display: block;
  color: #00765f;
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.workspace-title h1 {
  margin: 5px 0 6px;
  color: #10251f;
  font-size: 34px;
  letter-spacing: 0;
}

.workspace-title p {
  margin: 0;
  color: #657872;
  line-height: 1.7;
}

.workspace-tip {
  text-align: right;
}

.pipeline-card {
  margin-top: 16px;
  padding: 22px;
  border: 1px solid #dfeae6;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 18px 40px rgba(33, 73, 64, 0.07);
}

.pipeline-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 22px;
}

.pipeline-heading h2 {
  margin: 6px 0 0;
  color: #006b58;
  font-size: 26px;
  letter-spacing: 0;
}

.pipeline-heading p {
  max-width: 520px;
  margin: 0;
  color: #657872;
  line-height: 1.7;
  text-align: right;
}

.config-box {
  margin-top: 18px;
  padding: 16px;
  border: 1px solid #e0eee9;
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(237, 248, 244, 0.95), rgba(255, 255, 255, 0.9));
}

.config-box h3,
.final-box h3 {
  margin: 0 0 14px;
  color: #00765f;
  font-size: 20px;
  letter-spacing: 0;
}

.current-row {
  display: grid;
  align-items: center;
  gap: 18px;
}

.current-row.single {
  grid-template-columns: minmax(340px, 780px);
}

.current-row.double {
  grid-template-columns: minmax(300px, 1fr) 28px minmax(300px, 1fr);
}

.flow-item {
  min-height: 78px;
  padding: 12px;
  border-radius: 8px;
  background: #fff;
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr) 52px;
  gap: 12px;
  align-items: center;
  box-shadow: 0 10px 24px rgba(33, 73, 64, 0.08);
}

.flow-item > strong {
  width: 46px;
  height: 46px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: #008d72;
  color: #fff;
  font-size: 18px;
  font-weight: 900;
}

.flow-item h4 {
  margin: 0;
  color: #10251f;
  font-size: 15px;
}

.flow-item p {
  margin: 6px 0 0;
  color: #657872;
  font-size: 13px;
}

.move-actions {
  display: grid;
  gap: 6px;
}

.move-actions button {
  height: 26px;
  border: 1px solid #dfe8e4;
  border-radius: 7px;
  background: #f8fcfa;
  color: #6b7d77;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}

.flow-arrow {
  color: #008d72;
  font-size: 22px;
  text-align: center;
}

.option-grid {
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.option-card {
  position: relative;
  min-height: 82px;
  padding: 14px;
  border: 1px solid #e1e8e5;
  border-radius: 8px;
  background: #fff;
  text-align: left;
  cursor: default;
}

.option-card.selected {
  border-color: #88d0c2;
  background: linear-gradient(135deg, rgba(225, 246, 239, 0.96), rgba(255, 255, 255, 0.9));
  box-shadow: inset 0 0 0 1px rgba(0, 141, 114, 0.12);
}

.option-card span {
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  background: #edf3f0;
  color: #63756f;
  font-size: 12px;
  font-weight: 800;
}

.option-card.selected span {
  background: #dff7ee;
  color: #00765f;
}

.option-card strong {
  display: block;
  margin-top: 8px;
  color: #10251f;
  font-size: 15px;
}

.option-card small {
  display: block;
  margin-top: 5px;
  color: #657872;
}

.option-card svg {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 18px;
  height: 18px;
  color: #008d72;
}

.final-box {
  margin-top: 18px;
  padding: 16px;
  border: 1px solid #e1e8e5;
  border-radius: 8px;
  background: #fbfdfc;
}

.final-pills {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.final-pills span {
  height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  background: #e9f8f1;
  color: #00765f;
  font-weight: 800;
}

.final-pills b {
  color: #008d72;
}

.trace-summary {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.trace-summary article {
  padding: 14px 16px;
  border: 1px solid #dfeae6;
  border-radius: 8px;
  background: #fff;
}

.trace-summary span,
.trace-summary strong {
  display: block;
}

.trace-summary span {
  color: #657872;
  font-size: 13px;
}

.trace-summary strong {
  margin-top: 6px;
  color: #006b58;
  font-size: 18px;
}

.chunk-panel {
  max-width: 1660px;
  margin: 16px auto 0;
  padding: 22px;
  border: 1px solid #b9e0d7;
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(225, 246, 239, 0.94), rgba(255, 255, 255, 0.9) 42%),
    #fff;
  box-shadow: 0 18px 42px rgba(0, 141, 114, 0.10);
}

.chunk-actions {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  padding-bottom: 18px;
  border-bottom: 1px solid #dbeee9;
}

.count-pill,
.chunk-actions button,
.parent-actions span {
  height: 34px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 14px;
  background: #dff7ee;
  color: #00765f;
  font-weight: 900;
}

.chunk-actions > strong {
  color: #10251f;
  font-size: 18px;
}

.chunk-actions > div {
  display: flex;
  align-items: center;
  gap: 8px;
}

.chunk-actions button {
  border: none;
  background: transparent;
  color: #3f5f57;
  cursor: pointer;
}

.chunk-actions button.active {
  background: #fff;
  color: #006b58;
  box-shadow: 0 8px 18px rgba(0, 141, 114, 0.12);
}

.chunk-stats {
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.chunk-stats article {
  min-height: 82px;
  padding: 16px;
  border: 1px solid #d4e9e3;
  border-radius: 8px;
  background: linear-gradient(180deg, #fff, #f4fbf8);
}

.chunk-stats span,
.chunk-stats strong {
  display: block;
}

.chunk-stats span {
  color: #657872;
  font-size: 13px;
}

.chunk-stats strong {
  margin-top: 10px;
  color: #10251f;
  font-size: 26px;
}

.parent-block {
  margin-top: 16px;
  overflow: hidden;
  border: 1px solid #d5e7e2;
  border-radius: 8px;
  background: #fff;
}

.parent-block header {
  min-height: 104px;
  padding: 18px 20px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 18px;
  align-items: start;
  background: linear-gradient(135deg, rgba(248, 252, 250, 0.98), rgba(232, 246, 241, 0.58));
  border-bottom: 1px solid #e0eee9;
}

.parent-block h2 {
  margin: 0;
  color: #10251f;
  font-size: 18px;
}

.parent-block header p {
  margin: 12px 0 0;
  color: #6c807a;
}

.parent-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.parent-actions button {
  height: 40px;
  border: 1px solid #cfe7df;
  border-radius: 8px;
  background: #fff;
  color: #00765f;
  padding: 0 16px;
  font-weight: 900;
  cursor: pointer;
}

.parent-actions span {
  height: 28px;
  padding: 0 10px;
  font-size: 12px;
}

.chunk-token-row {
  min-height: 92px;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 10px;
  background: rgba(237, 248, 244, 0.45);
  border-bottom: 1px solid #e0eee9;
}

.chunk-token-row article {
  width: 100px;
  height: 72px;
  border: 1px solid #dbe7e3;
  border-radius: 8px;
  background: #fff;
  display: grid;
  place-items: center;
  box-shadow: 0 10px 22px rgba(33, 73, 64, 0.07);
}

.chunk-token-row strong,
.chunk-token-row span {
  display: block;
}

.chunk-token-row strong {
  color: #10251f;
  font-size: 18px;
}

.chunk-token-row span {
  color: #6b7d77;
  font-size: 12px;
  font-weight: 800;
}

.chunk-table-head,
.chunk-table-row {
  display: grid;
  grid-template-columns: 130px 1.5fr 1fr 90px 90px 2fr;
  gap: 16px;
  align-items: start;
}

.chunk-table-head {
  padding: 14px 20px;
  background: #f8fbfa;
  color: #627770;
  font-size: 13px;
  font-weight: 900;
}

.chunk-table-row {
  padding: 20px;
  border-top: 1px solid #edf3f1;
}

.chunk-table-row strong {
  color: #10251f;
}

.chunk-table-row p {
  margin: 8px 0 0;
  color: #657872;
  line-height: 1.6;
}

.chunk-table-row a {
  display: inline-block;
  margin-top: 10px;
  color: #00765f;
  font-size: 13px;
  font-weight: 900;
  text-decoration: none;
}

.status-tags {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  flex-wrap: wrap;
}

.status-tags span {
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  background: #edf3f0;
  color: #63756f;
  font-size: 12px;
  font-weight: 800;
}

.preview-text {
  font-size: 16px;
}

.floating-log-button {
  position: fixed;
  right: 28px;
  bottom: 28px;
  z-index: 20;
  height: 44px;
  border: none;
  border-radius: 999px;
  background: #008d72;
  color: #fff;
  padding: 0 10px 0 18px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-weight: 900;
  box-shadow: 0 14px 30px rgba(0, 141, 114, 0.24);
  cursor: pointer;
}

.floating-log-button span {
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.22);
}

.drawer-mask {
  position: fixed;
  inset: 0;
  z-index: 60;
  background: rgba(16, 37, 31, 0.42);
  display: flex;
  justify-content: flex-end;
}

.task-drawer {
  width: min(620px, 92vw);
  height: 100%;
  padding: 24px;
  background: #fff;
  overflow: auto;
  box-shadow: -18px 0 38px rgba(16, 37, 31, 0.16);
}

.task-drawer header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.task-drawer h2 {
  margin: 0;
  color: #10251f;
  font-size: 20px;
}

.task-drawer header p {
  margin: 12px 0 0;
  color: #657872;
  font-size: 16px;
}

.task-drawer header button {
  width: 40px;
  height: 40px;
  border: 1px solid #dfe8e4;
  border-radius: 8px;
  background: #fff;
  color: #00765f;
  font-size: 24px;
  cursor: pointer;
}

.status-row {
  margin-top: 18px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.status-row span {
  min-height: 42px;
  padding: 0 14px;
  border: 1px solid #dfe8e4;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  background: #f8fcfa;
  color: #344b44;
  font-weight: 900;
}

.status-row b {
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  background: #dff7ee;
  color: #00765f;
}

.log-timeline {
  position: relative;
  margin: 22px 0 0;
  padding: 0 0 0 28px;
  list-style: none;
}

.log-timeline::before {
  content: "";
  position: absolute;
  top: 0;
  bottom: 0;
  left: 8px;
  width: 2px;
  background: #d3e7e1;
}

.log-timeline li {
  position: relative;
  margin-bottom: 16px;
  padding: 16px;
  border: 1px solid #e0eee9;
  border-radius: 8px;
  background: #fbfdfc;
}

.log-timeline li::before {
  content: "";
  position: absolute;
  top: 20px;
  left: -25px;
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: #008d72;
  box-shadow: 0 0 0 4px #dff7ee;
}

.log-timeline strong {
  display: block;
  color: #10251f;
  font-size: 16px;
}

.log-timeline strong span {
  color: #657872;
  font-weight: 700;
}

.log-timeline p {
  margin: 12px 0;
  color: #344b44;
  line-height: 1.7;
}

.log-timeline code {
  display: block;
  padding: 12px;
  border-radius: 7px;
  background: #10251f;
  color: #ecfdf7;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 1180px) {
  .stage-shell,
  .trace-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .workspace-title {
    grid-template-columns: 1fr;
  }

  .workspace-tip,
  .pipeline-heading p {
    text-align: left;
  }

  .pipeline-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .current-row.single,
  .current-row.double {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .parse-page {
    padding: 14px;
  }

  .stage-shell,
  .option-grid,
  .trace-summary {
    grid-template-columns: 1fr;
  }

  .stage-card,
  .flow-item {
    grid-template-columns: 46px minmax(0, 1fr);
  }

  .stage-card > span,
  .move-actions {
    grid-column: 1 / -1;
  }
}
</style>
