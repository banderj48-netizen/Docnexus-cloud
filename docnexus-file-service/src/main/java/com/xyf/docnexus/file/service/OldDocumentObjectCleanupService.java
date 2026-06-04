package com.xyf.docnexus.file.service;

import com.xyf.docnexus.file.entity.DocumentFile;
import com.xyf.docnexus.file.mapper.DocumentFileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 旧文档对象异步清理服务。
 *
 * <p>保存新版本时数据库仍保留 revision 记录，这里只在事务提交后清理旧 MinIO object，避免事务回滚时误删原文件。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OldDocumentObjectCleanupService {

    private final ObjectStorageService objectStorageService;
    private final DocumentFileMapper documentFileMapper;

    /**
     * 在当前事务提交后异步删除旧对象；没有事务时直接异步删除。
     */
    public void deleteAfterCommit(Long userId,
                                  String fileId,
                                  String oldBucket,
                                  String oldObjectKey,
                                  String newBucket,
                                  String newObjectKey,
                                  String eventId) {
        if (!StringUtils.hasText(oldBucket) || !StringUtils.hasText(oldObjectKey)) {
            return;
        }
        if (sameObject(oldBucket, oldObjectKey, newBucket, newObjectKey)) {
            log.debug("跳过旧对象清理，新旧对象一致，fileId={}, objectKey={}", fileId, oldObjectKey);
            return;
        }
        Runnable cleanupTask = () -> CompletableFuture.runAsync(() ->
                deleteIfNotCurrent(userId, fileId, oldBucket, oldObjectKey, eventId));

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanupTask.run();
                }
            });
            return;
        }
        cleanupTask.run();
    }

    /**
     * 校验旧对象已经不是当前版本后再删除。
     */
    private void deleteIfNotCurrent(Long userId, String fileId, String oldBucket, String oldObjectKey, String eventId) {
        try {
            DocumentFile currentFile = documentFileMapper.selectByUserAndFileId(userId, fileId);
            if (currentFile != null && sameObject(oldBucket, oldObjectKey, currentFile.getBucketName(), currentFile.getObjectKey())) {
                log.warn("旧对象仍是当前版本，跳过删除，fileId={}, eventId={}, objectKey={}", fileId, eventId, oldObjectKey);
                return;
            }
            objectStorageService.removeObject(oldBucket, oldObjectKey);
            log.info("旧文档对象已异步清理，fileId={}, eventId={}, bucket={}, objectKey={}",
                    fileId, eventId, oldBucket, oldObjectKey);
        } catch (Exception exception) {
            log.warn("旧文档对象异步清理失败，fileId={}, eventId={}, bucket={}, objectKey={}",
                    fileId, eventId, oldBucket, oldObjectKey, exception);
        }
    }

    /**
     * 判断两个 MinIO 对象是否相同。
     */
    private boolean sameObject(String leftBucket, String leftObjectKey, String rightBucket, String rightObjectKey) {
        return Objects.equals(leftBucket, rightBucket) && Objects.equals(leftObjectKey, rightObjectKey);
    }
}
