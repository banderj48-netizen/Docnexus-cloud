const STORAGE_KEY = 'docnexus:deleting-file-records'
const EVENT_NAME = 'docnexus:file-deleting'
const MAX_RECORDS = 300
const RECORD_TTL = 24 * 60 * 60 * 1000

function now() {
  return Date.now()
}

function normalizeFileId(fileId) {
  return String(fileId || '').trim()
}

function readRecords() {
  try {
    return normalizeRecords(localStorage.getItem(STORAGE_KEY))
  } catch (error) {
    console.warn('读取本地资料删除状态失败，已按空集合处理', error)
    return []
  }
}

function normalizeRecords(raw) {
  try {
    const records = JSON.parse(raw || '[]')
    if (!Array.isArray(records)) return []
    const cutoff = now() - RECORD_TTL
    return records
      .filter((record) => record?.fileId && Number(record.deletedAt || 0) >= cutoff)
      .slice(-MAX_RECORDS)
  } catch {
    return []
  }
}

function writeRecords(records) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(records.slice(-MAX_RECORDS)))
  } catch (error) {
    console.warn('写入本地资料删除状态失败', error)
  }
}

export function getDeletingFileIds() {
  return new Set(readRecords().map((record) => record.fileId))
}

export function isFileDeleting(fileId) {
  const normalizedFileId = normalizeFileId(fileId)
  if (!normalizedFileId) return false
  return getDeletingFileIds().has(normalizedFileId)
}

export function filterDeletingFiles(files = []) {
  const deletingFileIds = getDeletingFileIds()
  return (Array.isArray(files) ? files : []).filter((file) => {
    const fileId = normalizeFileId(file?.fileId || file?.id)
    return !fileId || !deletingFileIds.has(fileId)
  })
}

export function markFileDeleting(fileId) {
  const normalizedFileId = normalizeFileId(fileId)
  if (!normalizedFileId) return

  /*
   * 资料删除现在走 MQ 异步清理。前端需要先记住“这个资料已经提交删除”，
   * 避免后台消费者尚未删 MySQL 时，其它页面再次查询列表又把它短暂显示出来。
   */
  const records = readRecords().filter((record) => record.fileId !== normalizedFileId)
  records.push({ fileId: normalizedFileId, deletedAt: now() })
  writeRecords(records)

  window.dispatchEvent(new CustomEvent(EVENT_NAME, { detail: { fileId: normalizedFileId } }))
}

export function subscribeFileDeleting(handler) {
  const customListener = (event) => {
    const fileId = normalizeFileId(event?.detail?.fileId)
    if (fileId) handler(fileId)
  }
  const storageListener = (event) => {
    if (event.key !== STORAGE_KEY) return
    /*
     * storage 事件只会在其它浏览器标签页触发。这里比较新旧记录，
     * 只把新增的 fileId 通知给当前页面，避免历史删除记录导致列表数量重复扣减。
     */
    const previousFileIds = new Set(normalizeRecords(event.oldValue).map((record) => record.fileId))
    normalizeRecords(event.newValue)
      .map((record) => record.fileId)
      .filter((fileId) => !previousFileIds.has(fileId))
      .forEach((fileId) => handler(fileId))
  }

  window.addEventListener(EVENT_NAME, customListener)
  window.addEventListener('storage', storageListener)

  return () => {
    window.removeEventListener(EVENT_NAME, customListener)
    window.removeEventListener('storage', storageListener)
  }
}
