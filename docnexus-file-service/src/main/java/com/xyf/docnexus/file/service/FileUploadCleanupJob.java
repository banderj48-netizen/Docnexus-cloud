package com.xyf.docnexus.file.service;

import com.xyf.docnexus.file.entity.FileUploadSession;
import com.xyf.docnexus.file.mapper.FileUploadChunkMapper;
import com.xyf.docnexus.file.mapper.FileUploadSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 上传临时会话清理任务。
 *
 * <p>清理长期未恢复的上传会话、Redis 队列项和 MinIO 临时分片，避免临时 bucket 持续膨胀。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileUploadCleanupJob {

    private static final String CLEANUP_LOCK_KEY = "lock:file:upload:cleanup";
    private static final int CLEANUP_LIMIT = 100;

    private final FileUploadSessionMapper uploadSessionMapper;
    private final FileUploadChunkMapper uploadChunkMapper;
    private final ObjectStorageService objectStorageService;
    private final FileCacheService fileCacheService;

    /**
     * 每 10 分钟清理一批过期上传会话。
     */
    @Scheduled(fixedDelay = 600000L, initialDelay = 60000L)
    public void cleanupExpiredUploads() {
        String token = fileCacheService.tryLock(CLEANUP_LOCK_KEY, Duration.ofMinutes(9));
        if (token == null) {
            return;
        }
        try {
            var expiredSessions = uploadSessionMapper.selectExpiredSessions(LocalDateTime.now(), CLEANUP_LIMIT);
            for (FileUploadSession session : expiredSessions) {
                try {
                    var chunks = uploadChunkMapper.findUploadedChunks(session.getUploadId());
                    objectStorageService.removeTempChunks(chunks);
                    uploadChunkMapper.deleteByUploadId(session.getUploadId());
                    uploadSessionMapper.markExpired(session.getUploadId());
                    fileCacheService.removeUploadItem(session.getUserId(), session.getUploadId());
                } catch (Exception exception) {
                    log.warn("清理过期上传会话失败，uploadId={}", session.getUploadId(), exception);
                }
            }
        } finally {
            fileCacheService.unlock(CLEANUP_LOCK_KEY, token);
        }
    }
}
