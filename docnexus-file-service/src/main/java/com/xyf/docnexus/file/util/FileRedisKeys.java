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
}
