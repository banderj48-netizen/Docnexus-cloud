import { reactive } from 'vue'
import { fileApi } from '../api/file'
import { logsApi } from '../api/logs'
import { STORAGE_KEYS } from '../constants'

const CACHE_TTL_MS = 5 * 60 * 1000
const CACHE_PREFIX = 'docnexus_sidebar_stats'

export const sidebarStatsState = reactive({
  fileCount: 0,
  indexedCount: 0,
  logCount: 0,
  loaded: false,
  loading: false,
})

let fileCountPromise = null
let logSummaryPromise = null
let logCacheInvalidatePromise = null

/**
 * 读取当前用户缓存命名空间，避免切换账号后复用旧用户的统计数据。
 */
function currentUserKey() {
  try {
    const user = JSON.parse(localStorage.getItem(STORAGE_KEYS.USER_INFO) || '{}')
    return String(user.userId || user.id || user.username || localStorage.getItem('userName') || 'anonymous')
  } catch {
    return String(localStorage.getItem('userName') || 'anonymous')
  }
}

/**
 * 生成当前用户的 sessionStorage 缓存 key。
 */
function cacheKey(name) {
  return `${CACHE_PREFIX}:${currentUserKey()}:${name}`
}

/**
 * 读取未过期的缓存数据。
 */
function readCache(name) {
  try {
    const raw = sessionStorage.getItem(cacheKey(name))
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (!parsed?.cachedAt || Date.now() - parsed.cachedAt > CACHE_TTL_MS) {
      sessionStorage.removeItem(cacheKey(name))
      return null
    }
    return parsed.data
  } catch {
    return null
  }
}

/**
 * 写入当前用户会话级缓存。
 */
function writeCache(name, data) {
  sessionStorage.setItem(cacheKey(name), JSON.stringify({
    cachedAt: Date.now(),
    data,
  }))
}

/**
 * 从统计项中兼容读取名称字段。
 */
function statName(item) {
  return item?.name ?? item?.NAME ?? ''
}

/**
 * 从统计项中兼容读取数值字段。
 */
function statValue(item) {
  return Number(item?.value ?? item?.VALUE ?? 0)
}

/**
 * 计算用户日志主动操作总数。
 */
export function countUserOperations(summary) {
  return (summary?.successStatus || [])
    .filter(item => ['SUCCESS', 'FAILED'].includes(statName(item)))
    .reduce((sum, item) => sum + statValue(item), 0)
}

/**
 * 让用户日志统计缓存失效，用于修改密码、修改资料、上传文档等主动操作成功后重新拉取后端统计。
 */
export function invalidateUserOperationSummary({ remote = false } = {}) {
  sessionStorage.removeItem(cacheKey('logSummary'))
  if (!remote) {
    return Promise.resolve()
  }
  if (!logCacheInvalidatePromise) {
    logCacheInvalidatePromise = logsApi.invalidateUserOperationCache()
      .catch(() => {})
      .finally(() => {
        logCacheInvalidatePromise = null
      })
  }
  return logCacheInvalidatePromise
}

/**
 * 用户主动业务操作已经发起，清理前端 summary 缓存并通知后端删除 Redis 缓存。
 */
export function notifyUserOperationChanged() {
  invalidateUserOperationSummary({ remote: true })
}

/**
 * 获取文档库文件总数，优先使用 sessionStorage 缓存。
 */
export async function fetchSidebarFileCount({ force = false } = {}) {
  if (!force) {
    const cached = readCache('fileCount')
    if (cached !== null && cached !== undefined) {
      sidebarStatsState.fileCount = Number(cached) || 0
      return sidebarStatsState.fileCount
    }
  }
  if (fileCountPromise && !force) {
    return fileCountPromise
  }
  fileCountPromise = fileApi.getFileList(
    { pageNum: 1, pageSize: 1, knowledgeBaseId: 'default' },
    { silent: true },
  ).then((res) => {
    const count = Number(res?.data?.total ?? 0)
    sidebarStatsState.fileCount = count
    writeCache('fileCount', count)
    return count
  }).catch(() => {
    return sidebarStatsState.fileCount
  }).finally(() => {
    fileCountPromise = null
  })
  return fileCountPromise
}

/**
 * 获取用户日志统计，优先使用 sessionStorage 缓存。
 */
export async function fetchUserOperationSummary({ force = false, silent = true } = {}) {
  if (!force) {
    const cached = readCache('logSummary')
    if (cached) {
      sidebarStatsState.logCount = countUserOperations(cached)
      return cached
    }
  }
  if (logSummaryPromise && !force) {
    return logSummaryPromise
  }
  logSummaryPromise = logsApi.userOperationSummary({ silent }).then((res) => {
    const summary = res?.data || { successStatus: [], functionStats: [], days: 5 }
    sidebarStatsState.logCount = countUserOperations(summary)
    writeCache('logSummary', summary)
    return summary
  }).catch(() => {
    return readCache('logSummary') || { successStatus: [], functionStats: [], days: 5 }
  }).finally(() => {
    logSummaryPromise = null
  })
  return logSummaryPromise
}

/**
 * 加载侧边栏所需统计数据，同一用户同一会话内不会重复请求相同数据。
 */
export async function ensureSidebarStats({ force = false } = {}) {
  sidebarStatsState.loading = true
  try {
    const [fileCount, logSummary] = await Promise.all([
      fetchSidebarFileCount({ force }),
      fetchUserOperationSummary({ force, silent: true }),
    ])
    sidebarStatsState.fileCount = Number(fileCount) || 0
    sidebarStatsState.logCount = countUserOperations(logSummary)
    sidebarStatsState.loaded = true
    return sidebarStatsState
  } finally {
    sidebarStatsState.loading = false
  }
}

/**
 * 本地增减文档数量，并同步更新会话缓存。
 */
export function adjustSidebarFileCount(delta) {
  sidebarStatsState.fileCount = Math.max(0, Number(sidebarStatsState.fileCount || 0) + delta)
  writeCache('fileCount', sidebarStatsState.fileCount)
}

/**
 * 用页面已有的后端分页 total 直接回写文档数量缓存。
 */
export function setSidebarFileCount(count) {
  sidebarStatsState.fileCount = Math.max(0, Number(count) || 0)
  writeCache('fileCount', sidebarStatsState.fileCount)
}

/**
 * 使文档数量缓存失效，下次进入布局时重新从后端获取。
 */
export function invalidateSidebarFileCount() {
  sessionStorage.removeItem(cacheKey('fileCount'))
}
