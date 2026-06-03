<template>
  <StudioLayout>
    <div class="user-log-page">
      <header class="log-header">
        <div>
          <span class="eyebrow">User Logs</span>
          <h1>用户日志</h1>
          <p>最近 5 天由你主动发起的业务操作记录</p>
        </div>
        <button class="refresh-button" type="button" :disabled="loading" @click="refreshAll(true)">
          <el-icon><Refresh /></el-icon>
          <span>{{ loading ? '刷新中' : '刷新' }}</span>
        </button>
      </header>

      <section class="summary-row">
        <article class="summary-card total-card">
          <span>主动操作</span>
          <strong>{{ totalOperationCount }}</strong>
          <p>仅统计用户点击或提交触发的功能</p>
        </article>
        <div class="summary-side">
          <article class="summary-card mini success">
            <span>成功</span>
            <strong>{{ successCount }}</strong>
            <p>成功完成并记录耗时的操作</p>
          </article>
          <article class="summary-card mini danger">
            <span>失败</span>
            <strong>{{ failedCount }}</strong>
            <p>失败记录会展示简短告警信息</p>
          </article>
        </div>
      </section>

      <section class="chart-grid">
        <article class="chart-panel">
          <div class="panel-title">
            <h2>操作结果</h2>
            <button v-if="selectedStatusKey" type="button" @click="clearStatusFilter">清除筛选</button>
          </div>
          <div class="chart-body">
            <svg class="pie-chart" viewBox="0 0 42 42" aria-label="操作结果统计">
              <circle class="pie-bg" cx="21" cy="21" r="15.915" />
              <circle
                v-for="segment in resultSegments"
                :key="segment.key"
                class="pie-slice"
                cx="21"
                cy="21"
                r="15.915"
                :stroke="segment.color"
                :stroke-dasharray="`${segment.percent} ${100 - segment.percent}`"
                :stroke-dashoffset="segment.offset"
                @click="selectStatus(segment.key)"
              />
            </svg>
            <div class="legend-list">
              <button
                v-for="item in resultStats"
                :key="item.key"
                type="button"
                :class="{ active: selectedStatusKey === item.key }"
                @click="selectStatus(item.key)"
              >
                <i :style="{ backgroundColor: item.color }"></i>
                <span>{{ item.label }}</span>
                <strong>{{ item.value }}</strong>
              </button>
            </div>
          </div>
        </article>

        <article class="chart-panel">
          <div class="panel-title">
            <h2>业务功能</h2>
            <button v-if="selectedFunctionName" type="button" @click="clearFunctionFilter">清除筛选</button>
          </div>
          <div class="chart-body">
            <svg class="pie-chart" viewBox="0 0 42 42" aria-label="业务功能统计">
              <circle class="pie-bg" cx="21" cy="21" r="15.915" />
              <circle
                v-for="segment in functionSegments"
                :key="segment.key"
                class="pie-slice"
                cx="21"
                cy="21"
                r="15.915"
                :stroke="segment.color"
                :stroke-dasharray="`${segment.percent} ${100 - segment.percent}`"
                :stroke-dashoffset="segment.offset"
                @click="selectFunction(segment.name)"
              />
            </svg>
            <div class="legend-list">
              <button
                v-for="item in functionStats"
                :key="item.key"
                type="button"
                :class="{ active: selectedFunctionName === item.name }"
                @click="selectFunction(item.name)"
              >
                <i :style="{ backgroundColor: item.color }"></i>
                <span>{{ item.name }}</span>
                <strong>{{ item.value }}</strong>
              </button>
              <div v-if="!functionStats.length" class="chart-empty">暂无业务操作</div>
            </div>
          </div>
        </article>
      </section>

      <section class="log-table-panel">
        <div class="table-toolbar">
          <div>
            <h2>业务操作列表</h2>
            <p>{{ activeFilterText }}</p>
          </div>
          <div class="toolbar-actions">
            <select v-model="successFilter" @change="handleSuccessSelect">
              <option value="">全部结果</option>
              <option value="true">仅成功</option>
              <option value="false">仅失败</option>
            </select>
            <select v-model.number="pageSize" @change="reloadPage(1)">
              <option :value="10">10/页</option>
              <option :value="20">20/页</option>
              <option :value="50">50/页</option>
            </select>
          </div>
        </div>

        <div class="log-table">
          <div class="log-table-head">
            <span>发起时间</span>
            <span>业务功能</span>
            <span>操作名称</span>
            <span>结果</span>
            <span>告警信息</span>
            <span>耗时</span>
          </div>

          <div v-if="!records.length" class="empty-state">
            {{ loading ? '正在加载日志' : '暂无符合条件的业务操作' }}
          </div>

          <article v-for="row in records" :key="row.eventId" class="log-row">
            <span>{{ formatTime(row.occurredAt || row.createdAt) }}</span>
            <strong>{{ row.functionName || row.module || '-' }}</strong>
            <em>{{ row.operationName || '-' }}</em>
            <span class="status-pill" :class="{ failed: !row.success }">{{ row.success ? '成功' : '失败' }}</span>
            <span class="alert-cell">{{ row.alertMessage || '-' }}</span>
            <span>{{ row.durationMs == null ? '-' : `${row.durationMs} ms` }}</span>
          </article>
        </div>

        <div class="pagination-bar">
          <button type="button" :disabled="pageNum <= 1 || loading" @click="reloadPage(pageNum - 1)">上一页</button>
          <span>第 {{ pageNum }} / {{ totalPages || 1 }} 页，共 {{ total }} 条</span>
          <button type="button" :disabled="pageNum >= totalPages || loading" @click="reloadPage(pageNum + 1)">下一页</button>
        </div>
      </section>
    </div>
  </StudioLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import StudioLayout from '../components/StudioLayout.vue'
import { logsApi } from '../api/logs'
import { fetchUserOperationSummary } from '../utils/sidebarStats'

const loading = ref(false)
const summary = ref({ successStatus: [], functionStats: [], days: 5 })
const records = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const successFilter = ref('')
const selectedStatusKey = ref('')
const selectedFunctionName = ref('')

const palette = ['#1f9d68', '#d95656', '#4d8068', '#e5a33a', '#5f7f96', '#8a6bcb']

const normalizeName = item => item?.name ?? item?.NAME ?? ''
const normalizeValue = item => Number(item?.value ?? item?.VALUE ?? 0)

const resultStats = computed(() => {
  const values = new Map((summary.value.successStatus || []).map(item => [normalizeName(item), normalizeValue(item)]))
  return [
    { key: 'SUCCESS', label: '成功', color: '#1f9d68', value: values.get('SUCCESS') || 0 },
    { key: 'FAILED', label: '失败', color: '#d95656', value: values.get('FAILED') || 0 },
  ]
})

const functionStats = computed(() => (
  (summary.value.functionStats || [])
    .map((item, index) => ({
      key: normalizeName(item) || `FUNCTION_${index}`,
      name: normalizeName(item) || '其他功能',
      value: normalizeValue(item),
      color: palette[index % palette.length],
    }))
    .filter(item => item.value > 0)
))

const successCount = computed(() => resultStats.value.find(item => item.key === 'SUCCESS')?.value || 0)
const failedCount = computed(() => resultStats.value.find(item => item.key === 'FAILED')?.value || 0)
const totalOperationCount = computed(() => successCount.value + failedCount.value)
const totalPages = computed(() => Math.ceil(total.value / pageSize.value))

const activeFilterText = computed(() => {
  const filters = []
  if (selectedStatusKey.value) filters.push(selectedStatusKey.value === 'SUCCESS' ? '成功' : '失败')
  if (selectedFunctionName.value) filters.push(selectedFunctionName.value)
  return filters.length ? `当前筛选：${filters.join(' / ')}` : '展示最近 5 天主动业务操作'
})

/**
 * 根据统计数据生成 SVG 饼图片段。
 */
const buildSegments = (items) => {
  const totalValue = items.reduce((sum, item) => sum + item.value, 0)
  let offset = 25
  return items
    .filter(item => item.value > 0 && totalValue > 0)
    .map((item) => {
      const percent = (item.value / totalValue) * 100
      const segment = { ...item, percent, offset }
      offset -= percent
      return segment
    })
}

const resultSegments = computed(() => buildSegments(resultStats.value))
const functionSegments = computed(() => buildSegments(functionStats.value))

/**
 * 刷新统计数据。
 */
const loadSummary = async (force = false) => {
  summary.value = await fetchUserOperationSummary({ force, silent: false })
}

/**
 * 分页加载当前筛选条件下的用户业务日志。
 */
const loadOperations = async (targetPage = pageNum.value) => {
  const params = {
    pageNum: targetPage,
    pageSize: pageSize.value,
  }
  if (successFilter.value !== '') {
    params.success = successFilter.value === 'true'
  }
  if (selectedFunctionName.value) {
    params.functionName = selectedFunctionName.value
  }
  const res = await logsApi.listUserOperations(params)
  const page = res.data || {}
  records.value = page.records || []
  total.value = page.total || 0
  pageNum.value = page.pageNum || targetPage
}

/**
 * 刷新整页数据。
 */
const refreshAll = async (force = false) => {
  loading.value = true
  try {
    await loadSummary(force === true)
    await loadOperations(1)
  } finally {
    loading.value = false
  }
}

/**
 * 切换分页。
 */
const reloadPage = async (targetPage = 1) => {
  loading.value = true
  try {
    await loadOperations(targetPage)
  } finally {
    loading.value = false
  }
}

/**
 * 通过结果饼图切换成功/失败筛选。
 */
const selectStatus = async (key) => {
  selectedStatusKey.value = selectedStatusKey.value === key ? '' : key
  successFilter.value = selectedStatusKey.value === 'SUCCESS'
    ? 'true'
    : selectedStatusKey.value === 'FAILED'
      ? 'false'
      : ''
  await reloadPage(1)
}

/**
 * 清除成功/失败筛选。
 */
const clearStatusFilter = async () => {
  selectedStatusKey.value = ''
  successFilter.value = ''
  await reloadPage(1)
}

/**
 * 通过功能饼图切换业务功能筛选。
 */
const selectFunction = async (name) => {
  selectedFunctionName.value = selectedFunctionName.value === name ? '' : name
  await reloadPage(1)
}

/**
 * 清除业务功能筛选。
 */
const clearFunctionFilter = async () => {
  selectedFunctionName.value = ''
  await reloadPage(1)
}

/**
 * 处理成功/失败下拉筛选。
 */
const handleSuccessSelect = async () => {
  selectedStatusKey.value = successFilter.value === 'true'
    ? 'SUCCESS'
    : successFilter.value === 'false'
      ? 'FAILED'
      : ''
  await reloadPage(1)
}

/**
 * 格式化后端 LocalDateTime。
 */
const formatTime = value => (value ? String(value).replace('T', ' ').slice(0, 19) : '-')

onMounted(() => refreshAll(true))
</script>

<style scoped>
.user-log-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px;
  color: #1f2f2a;
}

.log-header,
.summary-row,
.chart-grid,
.panel-title,
.table-toolbar,
.pagination-bar {
  display: flex;
  gap: 14px;
}

.log-header,
.panel-title,
.table-toolbar,
.pagination-bar {
  align-items: center;
  justify-content: space-between;
}

.eyebrow {
  color: #1f9d68;
  font-weight: 700;
}

.log-header h1 {
  margin: 6px 0;
  font-size: 30px;
  letter-spacing: 0;
}

.log-header p,
.summary-card p,
.table-toolbar p {
  color: #65766e;
}

.refresh-button,
.panel-title button,
.pagination-bar button {
  border: 1px solid #bdd7ca;
  background: #fff;
  color: #285b47;
  border-radius: 8px;
  padding: 9px 13px;
  cursor: pointer;
}

.refresh-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #1f9d68;
  color: #fff;
  border-color: #1f9d68;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.summary-row {
  display: grid;
  grid-template-columns: minmax(150px, 1fr) minmax(112px, 0.62fr);
  align-items: stretch;
  gap: 12px;
}

.summary-side {
  display: grid;
  grid-template-rows: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.summary-card,
.chart-panel,
.log-table-panel {
  background: #fff;
  border: 1px solid #d8e6df;
  border-radius: 8px;
  box-shadow: 0 12px 30px rgba(31, 80, 60, 0.08);
  padding: 18px;
}

.summary-card strong {
  display: block;
  margin: 8px 0;
  font-size: 28px;
  color: #285b47;
}

.summary-card p {
  margin: 0;
  color: #637a70;
  line-height: 1.5;
}

.total-card {
  min-height: 168px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.total-card strong {
  font-size: 44px;
  line-height: 1;
}

.summary-card.mini {
  min-height: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 14px 16px;
}

.summary-card.mini strong {
  margin: 5px 0;
  font-size: 24px;
}

.summary-card.mini p {
  display: none;
}

.summary-card.success strong {
  color: #1f9d68;
}

.summary-card.danger strong {
  color: #d95656;
}

.panel-title h2,
.table-toolbar h2 {
  margin: 0;
  font-size: 18px;
  letter-spacing: 0;
}

.chart-body {
  display: grid;
  grid-template-columns: 180px 1fr;
  align-items: center;
  gap: 18px;
  margin-top: 14px;
}

.pie-chart {
  width: 180px;
  height: 180px;
  transform: rotate(-90deg);
}

.pie-bg,
.pie-slice {
  fill: none;
  stroke-width: 8;
}

.pie-bg {
  stroke: #edf4f0;
}

.pie-slice {
  cursor: pointer;
  transition: opacity 0.2s ease;
}

.pie-slice:hover {
  opacity: 0.75;
}

.legend-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.legend-list button {
  display: grid;
  grid-template-columns: 10px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  border: 1px solid transparent;
  background: #f7fbf9;
  border-radius: 8px;
  padding: 9px 10px;
  color: #2d463b;
  cursor: pointer;
}

.legend-list button.active {
  border-color: #1f9d68;
  background: #effaf5;
}

.legend-list i {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.legend-list span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chart-empty {
  color: #7b8d85;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-actions select {
  height: 36px;
  border: 1px solid #bdd7ca;
  border-radius: 8px;
  color: #285b47;
  background: #fff;
  padding: 0 10px;
}

.log-table {
  margin-top: 14px;
  border: 1px solid #e2ebe6;
  border-radius: 8px;
  overflow: hidden;
}

.log-table-head,
.log-row {
  display: grid;
  grid-template-columns: 170px minmax(120px, 1fr) minmax(150px, 1.1fr) 80px minmax(160px, 1.2fr) 90px;
  gap: 12px;
  align-items: center;
  padding: 12px 14px;
}

.log-table-head {
  background: #eff8f4;
  color: #466759;
  font-weight: 700;
}

.log-row {
  border-top: 1px solid #edf3f0;
  color: #40544b;
}

.log-row strong,
.log-row em,
.alert-cell {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-style: normal;
}

.status-pill {
  width: 58px;
  border-radius: 999px;
  background: #e9f8f1;
  color: #167b52;
  text-align: center;
  padding: 5px 0;
  font-weight: 700;
}

.status-pill.failed {
  background: #fff0ee;
  color: #c44d43;
}

.empty-state {
  padding: 28px;
  text-align: center;
  color: #7b8d85;
}

button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

@media (max-width: 1180px) {
  .chart-grid,
  .chart-body {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 860px) {
  .log-header,
  .table-toolbar,
  .pagination-bar {
    align-items: stretch;
    flex-direction: column;
  }

  .log-table-head,
  .log-row {
    grid-template-columns: 1fr;
  }

  .status-pill {
    width: 100%;
  }

  .summary-row {
    grid-template-columns: minmax(0, 1.05fr) minmax(98px, 0.72fr);
    gap: 10px;
  }

  .summary-card {
    padding: 14px;
  }

  .total-card {
    min-height: 150px;
  }

  .total-card strong {
    font-size: 40px;
  }

  .summary-card.mini {
    padding: 10px 12px;
  }

  .summary-card.mini strong {
    font-size: 22px;
  }
}
</style>
