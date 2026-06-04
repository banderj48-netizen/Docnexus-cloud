package com.xyf.docnexus.file.service.impl;

import com.xyf.docnexus.file.entity.DocumentFile;
import com.xyf.docnexus.file.mapper.DocumentFileMapper;
import com.xyf.docnexus.file.service.DocumentFileLookupService;
import com.xyf.docnexus.file.service.FileCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 基于 Redis 的文档文件元数据查询服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisDocumentFileLookupService implements DocumentFileLookupService {

    private final DocumentFileMapper documentFileMapper;
    private final FileCacheService fileCacheService;

    /**
     * 查询并校验当前用户文件。
     */
    @Override
    public DocumentFile requireFile(Long userId, String fileId) {
        DocumentFile cached = fileCacheService.getFileMeta(userId, fileId);
        if (isVisibleFile(cached, userId, fileId)) {
            return cached;
        }
        if (cached != null && cached.getDeleted() != null && cached.getDeleted() == 1) {
            throw new IllegalArgumentException("文件不存在或无权访问");
        }

        DocumentFile file = documentFileMapper.selectByUserAndFileId(userId, fileId);
        if (file == null) {
            throw new IllegalArgumentException("文件不存在或无权访问");
        }
        fileCacheService.putFileMeta(file);
        return file;
    }

    /**
     * 写入单文件元数据缓存。
     */
    @Override
    public void cacheFile(DocumentFile file) {
        fileCacheService.putFileMeta(file);
    }

    /**
     * 在事务提交后写入单文件元数据缓存。
     */
    @Override
    public void cacheFileAfterCommit(DocumentFile file) {
        if (file == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cacheFile(file);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * 事务提交后刷新 Redis，避免数据库回滚时缓存提前变成新状态。
             */
            @Override
            public void afterCommit() {
                cacheFile(file);
            }
        });
    }

    /**
     * 判断缓存中的文件是否可直接返回。
     */
    private boolean isVisibleFile(DocumentFile file, Long userId, String fileId) {
        return file != null
                && userId != null
                && userId.equals(file.getUserId())
                && fileId != null
                && fileId.equals(file.getFileId())
                && (file.getDeleted() == null || file.getDeleted() == 0);
    }
}
