/**
 * @description 文件模块 API 接口
 */
import request from '../utils/request'

const NORMAL_UPLOAD_LIMIT = 20 * 1024 * 1024
const MAX_UPLOAD_LIMIT = 100 * 1024 * 1024
const CHUNK_SIZE = 10 * 1024 * 1024

export const fileApi = {
    /**
     * 获取文件列表 (支持分页)
     * @param {Object} params { page: 1, size: 10, sortBy: 'createTime', direction: 'desc' }
     */
    getFileList: (params) => {
        return request.get('/files/list', { params })
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
            return Promise.reject(new Error('单个文件最大只能上传 100MB'))
        }
        if (rawFile.size > NORMAL_UPLOAD_LIMIT) {
            return fileApi.uploadByChunks(rawFile, options)
        }
        const formData = new FormData()
        formData.append('file', rawFile)

        return request.post('/files/upload', formData, {
            // 更加健壮的进度条监听
            onUploadProgress: (progressEvent) => {
                // 如果后端没返回总大小，我们设为 0
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
            throw new Error('单个文件最大只能上传 100MB')
        }
        const totalChunks = Math.ceil(rawFile.size / CHUNK_SIZE)
        const initRes = await request.post('/files/chunk/init', {
            fileName: rawFile.name,
            fileSize: rawFile.size,
            fileType: rawFile.type || rawFile.name.split('.').pop() || 'unknown',
            chunkSize: CHUNK_SIZE,
            totalChunks
        })

        const uploadId = initRes.data.uploadId
        const uploadedSet = new Set(initRes.data.uploadedChunks || [])
        const mode = 'chunk'

        for (let index = 0; index < totalChunks; index += 1) {
            if (uploadedSet.has(index)) {
                options.onProgress?.({
                    file: rawFile,
                    percent: Math.round(((index + 1) / totalChunks) * 100),
                    uploadedChunks: uploadedSet.size,
                    totalChunks,
                    mode
                })
                continue
            }

            const start = index * CHUNK_SIZE
            const end = Math.min(rawFile.size, start + CHUNK_SIZE)
            const formData = new FormData()
            formData.append('uploadId', uploadId)
            formData.append('chunkIndex', String(index))
            formData.append('chunk', rawFile.slice(start, end), rawFile.name + `.part${index}`)

            await request.post('/files/chunk/upload', formData, {
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
        }

        options.onProgress?.({
            file: rawFile,
            percent: 99,
            uploadedChunks: totalChunks,
            totalChunks,
            mode: 'merge'
        })

        const completeRes = await request.post('/files/chunk/complete', { uploadId }, { timeout: 600000 })
        options.onProgress?.({
            file: rawFile,
            percent: 100,
            uploadedChunks: totalChunks,
            totalChunks,
            mode: 'done'
        })
        return completeRes
    },

    getChunkStatus: (uploadId) => {
        return request.get(`/files/chunk/status/${uploadId}`)
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
     * 重新触发资料解析、切片、向量化和质量评估。
     * @param {String} fileId 文件ID
     */
    reindex: (fileId) => {
        return request.post(`/files/${fileId}/reindex`)
    }
}
