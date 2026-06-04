package com.xyf.docnexus.file.util;

/**
 * 文件服务 Redis Key 工具。
 */
public final class FileRedisKeys {

    private FileRedisKeys() {
    }

    /**
     * 用户文件列表缓存版本 Key。
     */
    public static String libraryVersionKey(Long userId, String knowledgeBaseId) {
        return "file:library:version:" + userId + ":" + knowledgeBaseId;
    }

    /**
     * 用户文件列表分页缓存 Key。
     */
    public static String libraryPageKey(Long userId, String knowledgeBaseId, long version, int pageNum, int pageSize) {
        return "file:library:page:" + userId + ":" + knowledgeBaseId + ":" + version + ":" + pageNum + ":" + pageSize;
    }

    /**
     * 用户文件服务缓存索引集合 Key。
     *
     * <p>集合中保存该用户在 file-service 产生的所有可清理缓存 Key。
     * 用户全部会话离线后通过 Lua 原子删除集合成员和集合自身，避免使用 scan 扫描 Redis。</p>
     */
    public static String userCacheSetKey(Long userId) {
        return "file:user:cache-keys:" + userId;
    }

    /**
     * 单文件元数据缓存 Key。
     */
    public static String fileMetaKey(Long userId, String fileId) {
        return "file:meta:" + userId + ":" + fileId;
    }

    /**
     * 文件列表回源互斥锁 Key。
     */
    public static String libraryLockKey(Long userId, String knowledgeBaseId) {
        return "lock:file:library:" + userId + ":" + knowledgeBaseId;
    }

    /**
     * 用户上传会话集合 Key。
     */
    public static String uploadUserSetKey(Long userId) {
        return "file:upload:user:" + userId;
    }

    /**
     * 上传临时状态 Key。
     */
    public static String uploadItemKey(String uploadId) {
        return "file:upload:item:" + uploadId;
    }

    /**
     * 分片完成互斥锁 Key。
     */
    public static String uploadCompleteLockKey(String uploadId) {
        return "lock:file:upload:complete:" + uploadId;
    }

    /**
     * 在线编辑保存互斥锁 Key。
     */
    public static String editorSaveLockKey(String fileId) {
        return "lock:file:editor:save:" + fileId;
    }

    /**
     * OnlyOffice 手动强制保存回调结果 Key。
     */
    public static String onlyOfficeForceSaveResultKey(String requestId) {
        return "file:onlyoffice:forcesave:result:" + requestId;
    }

    /**
     * 用户手动触发解析的互斥锁 Key。
     */
    public static String manualParseLockKey(Long userId, String fileId) {
        return "lock:file:parse:" + userId + ":" + fileId;
    }

    /**
     * MQ 消费者执行解析前的互斥锁 Key。
     */
    public static String parseConsumeLockKey(Long userId, String fileId) {
        return "lock:file:parse:consume:" + userId + ":" + fileId;
    }
}
