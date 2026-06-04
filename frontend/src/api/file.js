/**
 * @description 文件模块 API 接口
 */
import request from '../utils/request'

const MB = 1024 * 1024
const DIRECT_UPLOAD_LIMIT = 5 * MB
const MAX_UPLOAD_LIMIT = 200 * 1024 * 1024
const MIN_CHUNK_SIZE = 5 * MB
const MAX_DYNAMIC_CHUNK_SIZE = 10 * MB

/**
 * 主动检查上传是否已被页面离开或用户取消中止，避免继续上传后续分片。
 */
const throwIfUploadAborted = (signal) => {
    if (!signal?.aborted) return
    const error = new Error('上传已中止')
    error.name = 'AbortError'
    throw error
}

/**
 * 按企业上传策略计算分片大小。
 *
 * 小于 5MB 的文件走普通上传；5MB 以上至少拆成 5MB 分片，
 * 单片大小不超过 10MB，避免 MinIO 服务端合并时遇到非末尾分片小于 5MiB 的限制。
 */
const resolveChunkSize = (rawFile) => {
    if (rawFile.size < DIRECT_UPLOAD_LIMIT) return 0
    return Math.ceil(Math.max(MIN_CHUNK_SIZE, Math.min(rawFile.size / 5, MAX_DYNAMIC_CHUNK_SIZE)))
}

export const fileApi = {
    /**
     * 获取文件列表 (支持分页)
     * @param {Object} params { page: 1, size: 10, sortBy: 'createTime', direction: 'desc' }
     */
    getFileList: (params, config = {}) => {
        return request.get('/files/list', { ...config, params })
    },

    /**
     * 搜索文件
     * @param {Object} params { keyword: '搜索词', page: 1, size: 10 }
     */
    searchFiles: (params) => {
        return request.get('/files/search', { params })
    },

    /**
     * 获取文件详细元数据
     * @param {String} fileId 文件唯一标识
     */
    getMetadata: (fileId) => {
        return request.get(`/files/metadata/${fileId}`)
    },


    /**
     * 单文件上传 (FormData 格式)
     * @param {File} rawFile 原生文件对象 (从 input type="file" 或 el-upload 中获取)
     */
    upload: (rawFile, options = {}) => {
        if (rawFile.size > MAX_UPLOAD_LIMIT) {
            return Promise.reject(new Error('单个文件最大只能上传 200MB'))
        }
        if (rawFile.size >= DIRECT_UPLOAD_LIMIT) {
            return fileApi.uploadByChunks(rawFile, options)
        }
        const formData = new FormData()
        formData.append('file', rawFile)
        if (options.knowledgeBaseId) {
            formData.append('knowledgeBaseId', options.knowledgeBaseId)
        }

        return request.post('/files/upload', formData, {
            signal: options.signal,
            // 复用原 DocAI-main 的 Axios 上传进度思路，小文件由浏览器真实上传事件驱动。
            onUploadProgress: (progressEvent) => {
                const total = progressEvent.total || 0;
                const loaded = progressEvent.loaded || 0;

                if (total > 0) {
                    const percent = Math.round((loaded * 100) / total);
                    options.onProgress?.({ file: rawFile, percent, loaded, total, mode: 'normal' });
                } else {
                    options.onProgress?.({ file: rawFile, percent: 0, loaded, total, mode: 'normal' });
                }
            }
        })
    },

    /**
     * 大文件分片上传。
     *
     * 浏览器端只负责切片和顺序上传，不把整个文件读入内存；后端负责断点续传状态、
     * 合并临时分片、上传 MinIO 和写入 MySQL 元数据。
     */
    uploadByChunks: async (rawFile, options = {}) => {
        if (rawFile.size > MAX_UPLOAD_LIMIT) {
            throw new Error('单个文件最大只能上传 200MB')
        }
        let chunkSize = Number(options.chunkSize || resolveChunkSize(rawFile))
        if (!chunkSize || chunkSize <= 0) {
            chunkSize = MIN_CHUNK_SIZE
        }
        chunkSize = Math.max(MIN_CHUNK_SIZE, chunkSize)
        let totalChunks = Math.ceil(rawFile.size / chunkSize)
        let uploadId = options.uploadId || ''
        let uploadedSet = new Set(options.uploadedChunks || [])

        throwIfUploadAborted(options.signal)
        if (!uploadId) {
            const initRes = await request.post('/files/multipart/init', {
                fileName: rawFile.name,
                fileSize: rawFile.size,
                mimeType: rawFile.type || 'application/octet-stream',
                chunkSize,
                totalChunks,
                knowledgeBaseId: options.knowledgeBaseId || 'default'
            }, { signal: options.signal })
            uploadId = initRes.data.uploadId
            chunkSize = Number(initRes.data.chunkSize || chunkSize)
            totalChunks = initRes.data.totalChunks || totalChunks
            uploadedSet = new Set(initRes.data.uploadedChunks || [])
            options.onSession?.({ uploadId, totalChunks, chunkSize })
        } else {
            const statusRes = await fileApi.getChunkStatus(uploadId, { signal: options.signal })
            chunkSize = Number(statusRes.data.chunkSize || chunkSize)
            totalChunks = statusRes.data.totalChunks || totalChunks
            uploadedSet = new Set(statusRes.data.uploadedChunkIndexes || options.uploadedChunks || [])
            options.onSession?.({ uploadId, totalChunks, chunkSize })
        }
        const mode = 'chunk'

        for (let index = 0; index < totalChunks; index += 1) {
            throwIfUploadAborted(options.signal)
            if (uploadedSet.has(index)) {
                options.onProgress?.({
                    file: rawFile,
                    percent: Math.min(99, Math.round((uploadedSet.size / totalChunks) * 100)),
                    uploadedChunks: uploadedSet.size,
                    totalChunks,
                    mode
                })
                continue
            }

            const start = index * chunkSize
            const end = Math.min(rawFile.size, start + chunkSize)
            const formData = new FormData()
            formData.append('uploadId', uploadId)
            formData.append('chunkIndex', String(index))
            formData.append('chunk', rawFile.slice(start, end), rawFile.name + `.part${index}`)

            await request.post('/files/multipart/chunk', formData, {
                signal: options.signal,
                timeout: 300000,
                onUploadProgress: (progressEvent) => {
                    const chunkLoaded = progressEvent.loaded || 0
                    const completedBytes = start + chunkLoaded
                    options.onProgress?.({
                        file: rawFile,
                        percent: Math.min(99, Math.round((completedBytes / rawFile.size) * 100)),
                        uploadedChunks: index,
                        totalChunks,
                        mode
                    })
                }
            })
            uploadedSet.add(index)
            options.onProgress?.({
                file: rawFile,
                percent: Math.min(99, Math.round((uploadedSet.size / totalChunks) * 100)),
                uploadedChunks: uploadedSet.size,
                totalChunks,
                mode
            })
        }

        options.onProgress?.({
            file: rawFile,
            percent: 99,
            uploadedChunks: totalChunks,
            totalChunks,
            mode: 'merge'
        })

        throwIfUploadAborted(options.signal)
        const completeRes = await request.post('/files/multipart/complete', { uploadId }, { signal: options.signal, timeout: 600000 })
        options.onProgress?.({
            file: rawFile,
            percent: 100,
            uploadedChunks: totalChunks,
            totalChunks,
            mode: 'done'
        })
        return completeRes
    },

    getChunkStatus: (uploadId, config = {}) => {
        return request.get(`/files/multipart/status/${uploadId}`, config)
    },

    /**
     * 取消分片上传或失败上传临时项
     * @param {String} uploadId 上传会话 ID
     */
    cancelUpload: (uploadId) => {
        return request.post(`/files/multipart/cancel/${uploadId}`)
    },

    /**
     * 丢弃本轮失败上传项
     * @param {Array<String>} uploadIds 上传会话 ID
     */
    discardFailedUploads: (uploadIds = []) => {
        return request.post('/files/uploads/discard-failed', { uploadIds })
    },

    /**
     * 页面离开时标记上传中断并清理失败项
     * @param {Array<String>} uploadIds 需要标记为中断的上传会话
     */
    interruptUploads: (uploadIds = []) => {
        return request.post('/files/uploads/interrupt', { uploadIds })
    },

    /**
     * 查询可恢复上传会话
     */
    getRecoverableUploads: () => {
        return request.get('/files/uploads/recoverable')
    },

    /**
     * 清理当前用户失败上传项
     */
    clearFailedUploads: () => {
        return request.post('/files/uploads/clear-failed')
    },

    /**
     * 文件下载 (返回二进制数据流 Blob)
     * @param {String} fileId 文件ID
     */
    download: (fileId) => {
        return request.get(`/files/download/${fileId}`, {
            responseType: 'blob'
        })
    },

    /**
     * 文件预览 (主要用于图片、PDF，返回流)
     * @param {String} fileId 文件ID
     */
    preview: (fileId) => {
        return request.get(`/files/preview/${fileId}`, {
            responseType: 'blob'
        })
    },

    /**
     * 打开文档编辑页，返回真实抽取内容或 PDF 预览地址。
     * @param {String} fileId 文件ID
     */
    openEditor: (fileId) => {
        return request.get(`/files/${fileId}/editor`)
    },

    /**
     * 获取 OnlyOffice 原格式编辑器配置。
     * @param {String} fileId 文件ID
     * @param {Object} config axios 配置
     */
    getOnlyOfficeConfig: (fileId, config = {}) => {
        return request.get(`/files/${fileId}/onlyoffice/config`, config)
    },

    /**
     * 触发 OnlyOffice 手动强制保存，等待后端确认覆盖 MinIO 后返回。
     * @param {String} fileId 文件ID
     * @param {Object} data { currentVersion, documentKey }
     */
    forceSaveOnlyOffice: (fileId, data) => {
        return request.post(`/files/${fileId}/onlyoffice/forcesave`, data, { timeout: 90000, silent: true })
    },

    /**
     * 保存在线编辑内容，后端会生成新文件版本并触发重新解析。
     * @param {String} fileId 文件ID
     * @param {Object} data { currentVersion, contentHtml, contentHash }
     */
    saveEditorContent: (fileId, data) => {
        return request.put(`/files/${fileId}/editor/content`, data)
    },


    /**
     * 删除文件
     * @param {String} fileId 文件ID
     */
    delete: (fileId) => {
        return request.delete(`/files/${fileId}`)
    },

    /**
     * 重命名文件
     * @param {String} fileId 文件ID
     * @param {String} newName 新文件名
     */
    rename: (fileId, newName) => {
        // 注意：newName 后端要求通过 @RequestParam 接收，所以放在 params 里
        return request.put(`/files/${fileId}/rename`, null, {
            params: { newName }
        })
    },

    /**
     * 用户手动触发资料解析或重新解析。
     * @param {String} fileId 文件ID
     */
    reindex: (fileId) => {
        return request.post(`/files/${fileId}/reindex`)
    }
}
