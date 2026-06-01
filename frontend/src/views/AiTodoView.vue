<template>
  <StudioLayout>
    <main class="ai-todo-page">
      <header class="ai-todo-header">
        <div>
          <span class="eyebrow">Knowledge Tasks</span>
          <h1>知识任务</h1>
          <p>把资料总结、知识库问答、关键词提取和引用检查拆成 Todo，AI Agent 只围绕文档库和知识库执行。</p>
        </div>
        <button class="primary-button" type="button" @click="createTodo">
          <Plus />
          新建任务
        </button>
      </header>

      <section class="todo-board">
        <aside class="todo-panel">
          <div class="todo-panel-header">
            <h2>任务编排</h2>
            <span>{{ todos.length }} 个任务</span>
          </div>

          <form class="todo-form" @submit.prevent="createTodo">
            <label>
              <span>任务名称</span>
              <input v-model.trim="draft.title" placeholder="例如：总结本周上传资料的核心观点" />
            </label>

            <label>
              <span>AI 执行说明</span>
              <textarea v-model.trim="draft.instruction" placeholder="说明 AI 需要读取哪些资料、关注哪些章节、返回摘要还是引用来源" />
            </label>

            <div class="todo-form-grid">
              <label>
                <span>任务类型</span>
                <select v-model="draft.type">
                  <option value="summary">资料摘要</option>
                  <option value="rag">资料问答</option>
                  <option value="keywords">关键词提取</option>
                  <option value="review">审阅检查</option>
                </select>
              </label>

              <label>
                <span>优先级</span>
                <select v-model="draft.priority">
                  <option value="high">高</option>
                  <option value="normal">中</option>
                  <option value="low">低</option>
                </select>
              </label>
            </div>

            <label>
              <span>关联资料或知识范围</span>
              <input v-model.trim="draft.sources" placeholder="可填写文件名、文档 ID 或知识库范围" />
            </label>

            <button class="primary-button wide" type="submit">
              <Plus />
              加入知识任务
            </button>
          </form>
        </aside>

        <section class="todo-lanes">
          <article v-for="lane in lanes" :key="lane.status" class="todo-lane">
            <div class="lane-title">
              <h2>{{ lane.label }}</h2>
              <span>{{ todosByStatus[lane.status].length }}</span>
            </div>

            <div class="todo-stack">
              <article v-for="todo in todosByStatus[lane.status]" :key="todo.id" class="todo-card">
                <div class="todo-card-top">
                  <span class="todo-type">{{ typeLabelMap[todo.type] }}</span>
                  <em :class="`priority-${todo.priority}`">{{ priorityLabelMap[todo.priority] }}</em>
                </div>

                <h3>{{ todo.title }}</h3>
                <p>{{ todo.instruction }}</p>

                <div v-if="todo.sources" class="todo-source">
                  <Link />
                  <span>{{ todo.sources }}</span>
                </div>

                <div class="todo-meta">
                  <span>{{ todo.updatedAt }}</span>
                  <span v-if="todo.result">{{ todo.result }}</span>
                </div>

                <div class="todo-actions">
                  <button type="button" :disabled="todo.status === 'running'" @click="runTodo(todo)">
                    <VideoPlay />
                    {{ todo.status === 'running' ? '执行中' : '让 AI 执行' }}
                  </button>
                  <button type="button" title="标记完成" @click="moveTodo(todo, 'done')">
                    <Check />
                  </button>
                  <button type="button" title="删除任务" @click="removeTodo(todo.id)">
                    <Delete />
                  </button>
                </div>
              </article>

              <div v-if="!todosByStatus[lane.status].length" class="empty-state">
                暂无{{ lane.label }}任务
              </div>
            </div>
          </article>
        </section>
      </section>
    </main>
  </StudioLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Check, Delete, Link, Plus, VideoPlay } from '@element-plus/icons-vue'
import StudioLayout from '../components/StudioLayout.vue'
import { aiTodoApi } from '../api/aiTodo'

const STORAGE_KEY = 'docnexus_ai_todos'

const lanes = [
  { status: 'pending', label: '待执行' },
  { status: 'running', label: '执行中' },
  { status: 'done', label: '已完成' },
]

const typeLabelMap = {
  summary: '资料摘要',
  rag: '资料问答',
  keywords: '关键词提取',
  review: '审阅检查',
}

const priorityLabelMap = {
  high: '高优先级',
  normal: '中优先级',
  low: '低优先级',
}

const emptyDraft = () => ({
  title: '',
  instruction: '',
  type: 'summary',
  priority: 'normal',
  sources: '',
})

const todos = ref([])
const draft = reactive(emptyDraft())

const todosByStatus = computed(() => {
  const grouped = {
    pending: [],
    running: [],
    done: [],
  }

  todos.value.forEach((todo) => {
    if (grouped[todo.status]) {
      grouped[todo.status].push(todo)
    }
  })

  return grouped
})

const saveTodos = () => {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(todos.value))
}

const normalizeTodo = (todo) => ({
  ...todo,
  updatedAt: todo.updatedAt || todo.updateTime || todo.createTime || nowText(),
})

const loadTodos = async () => {
  try {
    const response = await aiTodoApi.list()
    todos.value = Array.isArray(response.data) ? response.data.map(normalizeTodo) : []
    saveTodos()
  } catch {
    try {
      const cached = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
      todos.value = Array.isArray(cached) ? cached : []
    } catch {
      todos.value = []
    }
  }
}

const resetDraft = () => {
  Object.assign(draft, emptyDraft())
}

const nowText = () => {
  const date = new Date()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${month}-${day} ${hour}:${minute}`
}

const createTodo = async () => {
  if (!draft.title || !draft.instruction) {
    ElMessage.warning('请填写任务名称和 AI 执行说明')
    return
  }

  const payload = {
    title: draft.title,
    instruction: draft.instruction,
    type: draft.type,
    priority: draft.priority,
    sources: draft.sources,
    status: 'pending',
    result: '',
  }

  try {
    const response = await aiTodoApi.create(payload)
    todos.value.unshift(normalizeTodo(response.data))
  } catch {
    todos.value.unshift({
      id: crypto.randomUUID(),
      ...payload,
      updatedAt: nowText(),
    })
  }

  saveTodos()
  resetDraft()
  ElMessage.success('已加入知识任务')
}

const moveTodo = async (todo, status) => {
  todo.status = status
  todo.updatedAt = nowText()
  if (status === 'done' && !todo.result) {
    todo.result = '等待接入后端任务结果'
  }
  try {
    await aiTodoApi.update(todo.id, todo)
  } catch {
    // 后端暂不可用时保留本地状态，保证页面操作不断流。
  }
  saveTodos()
}

const runTodo = async (todo) => {
  try {
    const response = await aiTodoApi.run(todo.id)
    Object.assign(todo, normalizeTodo(response.data))
  } catch {
    todo.status = 'running'
    todo.result = 'AI 已接收任务，等待后端 Agent 执行'
    todo.updatedAt = nowText()
  } finally {
    saveTodos()
    ElMessage.success('已发送给 AI 执行队列')
  }
}

const removeTodo = async (id) => {
  try {
    await aiTodoApi.remove(id)
  } catch {
    // 后端暂不可用时仍允许清理本地演示数据。
  }
  todos.value = todos.value.filter((todo) => todo.id !== id)
  saveTodos()
}

onMounted(loadTodos)
</script>

<style scoped>
.ai-todo-page {
  min-height: 100%;
}

.ai-todo-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 18px;
}

.ai-todo-header h1 {
  font-size: 30px;
  line-height: 1.22;
}

.ai-todo-header p {
  max-width: 760px;
  margin-top: 8px;
  color: #5f6f85;
  line-height: 1.6;
}

.todo-board {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  gap: 16px;
}

.todo-panel,
.todo-lane {
  border: 1px solid #dbe3ee;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.05);
}

.todo-panel {
  padding: 18px;
}

.todo-panel-header,
.lane-title,
.todo-card-top,
.todo-meta,
.todo-actions,
.todo-source {
  display: flex;
  align-items: center;
}

.todo-panel-header,
.lane-title {
  justify-content: space-between;
  gap: 12px;
}

.todo-panel-header h2,
.lane-title h2 {
  font-size: 18px;
}

.todo-panel-header span,
.lane-title span {
  color: #64748b;
  font-size: 13px;
}

.todo-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 16px;
}

.todo-form label,
.todo-form label span {
  display: block;
}

.todo-form label span {
  margin-bottom: 7px;
  color: #475569;
  font-size: 13px;
  font-weight: 800;
}

.todo-form input,
.todo-form textarea,
.todo-form select {
  width: 100%;
  border: 1px solid #d7dee9;
  border-radius: 8px;
  background: #ffffff;
  color: #172033;
  outline: none;
}

.todo-form input,
.todo-form select {
  height: 40px;
  padding: 0 11px;
}

.todo-form textarea {
  min-height: 132px;
  resize: vertical;
  padding: 11px;
  line-height: 1.6;
}

.todo-form input:focus,
.todo-form textarea:focus,
.todo-form select:focus {
  border-color: #047857;
}

.todo-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.primary-button.wide {
  width: 100%;
}

.todo-lanes {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.todo-lane {
  min-width: 0;
  padding: 14px;
}

.todo-stack {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 12px;
}

.todo-card {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
  padding: 12px;
}

.todo-card-top {
  justify-content: space-between;
  gap: 8px;
}

.todo-type,
.todo-card-top em {
  border-radius: 999px;
  padding: 5px 8px;
  font-size: 12px;
  font-style: normal;
}

.todo-type {
  background: #e0f2fe;
  color: #0369a1;
}

.todo-card-top em {
  background: #eef2f7;
  color: #475569;
}

.todo-card-top .priority-high {
  background: #fee2e2;
  color: #b91c1c;
}

.todo-card-top .priority-low {
  background: #dcfce7;
  color: #047857;
}

.todo-card h3 {
  margin-top: 10px;
  font-size: 15px;
  line-height: 1.4;
}

.todo-card p {
  margin-top: 8px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.55;
}

.todo-source {
  gap: 6px;
  margin-top: 10px;
  color: #475569;
  font-size: 12px;
}

.todo-source svg {
  width: 15px;
  height: 15px;
  color: #047857;
}

.todo-source span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.todo-meta {
  justify-content: space-between;
  gap: 10px;
  margin-top: 10px;
  color: #94a3b8;
  font-size: 12px;
}

.todo-actions {
  gap: 8px;
  margin-top: 12px;
}

.todo-actions button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 34px;
  gap: 6px;
  border: 1px solid #d7dee9;
  border-radius: 8px;
  background: #ffffff;
  color: #172033;
  padding: 0 10px;
}

.todo-actions button:first-child {
  flex: 1;
  border-color: #047857;
  background: #047857;
  color: #ffffff;
  font-weight: 800;
}

.todo-actions button:disabled {
  cursor: wait;
  opacity: 0.7;
}

.todo-actions svg {
  width: 16px;
  height: 16px;
}

@media (max-width: 1240px) {
  .todo-board,
  .todo-lanes {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .ai-todo-header {
    flex-direction: column;
  }

  .todo-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
