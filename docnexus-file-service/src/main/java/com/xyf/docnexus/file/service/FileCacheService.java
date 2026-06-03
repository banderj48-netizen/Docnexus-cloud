package com.xyf.docnexus.file.service;

import com.xyf.docnexus.common.VO.PageResponse;
import com.xyf.docnexus.file.dto.FileViewResponse;

import java.time.Duration;
import java.util.List;

/**
 * 文件 Redis 缓存服务接口。
 */
public interface FileCacheService {

    /**
     * 读取当前文件列表缓存版本。
     */
    long currentVersion(Long userId, String knowledgeBaseId);

    /**
     * 递增文件列表缓存版本。
     */
    void increaseVersion(Long userId, String knowledgeBaseId);

    /**
     * 读取文件分页缓存。
     */
    PageResponse<FileViewResponse> getPage(Long userId, String knowledgeBaseId, long version, int pageNum, int pageSize);

    /**
     * 写入文件分页缓存。
     */
    void putPage(Long userId, String knowledgeBaseId, long version, int pageNum, int pageSize, PageResponse<FileViewResponse> page);

    /**
     * 尝试获取文件列表回源锁。
     */
    String tryLock(String lockKey, Duration ttl);

    /**
     * 释放文件列表回源锁。
     */
    void unlock(String lockKey, String token);

    /**
     * 短暂等待其他线程完成缓存回填。
     */
    void shortWait();

    /**
     * 保存上传临时状态。
     */
    void saveUploadItem(Long userId, FileViewResponse item);

    /**
     * 删除上传临时状态。
     */
    void removeUploadItem(Long userId, String uploadId);

    /**
     * 查询用户临时上传项。
     */
    List<FileViewResponse> listUploadItems(Long userId);
}
