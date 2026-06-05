/**
 * 函数功能：去掉文件名最后一个扩展名，用于页面展示纯文件名。
 */
export const removeFileExtension = (fileName) => {
  const value = String(fileName || '').trim()
  if (!value) return '未命名文档'
  return value.replace(/\.[^.\\/]+$/, '')
}

/**
 * 函数功能：解析文件扩展名，统一转为小写。
 */
export const resolveFileExtension = (fileName) => {
  const value = String(fileName || '').trim()
  if (!value.includes('.')) return ''
  return value.split('.').pop().toLowerCase()
}

/**
 * 函数功能：把具体扩展名归一为页面右侧或封面展示的文件类型。
 */
export const resolveFileTypeLabel = (fileName, fallback = '未知类型') => {
  const ext = resolveFileExtension(fileName)
  if (['doc', 'docx', 'wps', 'wpt', 'wpd'].includes(ext)) return 'WORD'
  if (['ppt', 'pptx', 'dps', 'dpt'].includes(ext)) return 'PPT'
  if (ext === 'pdf') return 'PDF'
  if (['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp', 'svg'].includes(ext)) return 'PIC'
  if (['txt', 'md', 'markdown'].includes(ext)) return 'TXT'
  return fallback
}

/**
 * 函数功能：组合展示文件信息，文件名不带后缀，类型单独显示。
 */
export const formatDisplayFileMeta = (fileName, typeLabel) => {
  return `${removeFileExtension(fileName)} · ${typeLabel || resolveFileTypeLabel(fileName)}`
}
