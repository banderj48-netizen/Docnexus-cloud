package com.xyf.docnexus.file.service.impl;

import com.xyf.docnexus.common.VO.PageResponse;
import com.xyf.docnexus.common.constant.MqTopicConstants;
import com.xyf.docnexus.file.config.FileServiceProperties;
import com.xyf.docnexus.file.dto.*;
import com.xyf.docnexus.file.entity.DocumentFile;
import com.xyf.docnexus.file.entity.DocumentProcessTask;
import com.xyf.docnexus.file.entity.FileUploadChunk;
import com.xyf.docnexus.file.entity.FileUploadSession;
import com.xyf.docnexus.file.mapper.DocumentFileMapper;
import com.xyf.docnexus.file.mapper.DocumentProcessTaskMapper;
import com.xyf.docnexus.file.mapper.FileUploadChunkMapper;
import com.xyf.docnexus.file.mapper.FileUploadSessionMapper;
import com.xyf.docnexus.file.service.FileCacheService;
import com.xyf.docnexus.file.service.DocumentFileLookupService;
import com.xyf.docnexus.file.service.FileService;
import com.xyf.docnexus.file.service.FileUploadFailureService;
import com.xyf.docnexus.file.service.ObjectStorageService;
import com.xyf.docnexus.file.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 文件业务服务实现。
 *
 * <p>该实现完成上传、分片、列表、下载和删除第一阶段闭环，真正解析能力只预留任务和状态。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "ppt", "pptx", "txt", "wps", "wpt", "dps", "dpt", "wpd"
    );
    private static final long MIN_MULTIPART_SIZE_BYTES = 5L * 1024 * 1024;
    private static final long MIN_CHUNK_SIZE_BYTES = 5L * 1024 * 1024;
    private static final long MAX_CHUNK_SIZE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_MANUAL_REPARSE_COUNT = 1;

    private final DocumentFileMapper documentFileMapper;
    private final FileUploadSessionMapper uploadSessionMapper;
    private final FileUploadChunkMapper uploadChunkMapper;
    private final DocumentProcessTaskMapper processTaskMapper;
    private final ObjectStorageService objectStorageService;
    private final FileCacheService fileCacheService;
    private final DocumentFileLookupService documentFileLookupService;
    private final FileUploadFailureService uploadFailureService;
    private final FileServiceProperties properties;
    private final ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;

    /**
     * 查询已上传文档列表。
     */
    @Override
    public PageResponse<FileViewResponse> list(Long userId, FileListRequest request) {
        int pageNum = normalizePageNum(request.getPageNum());
        int pageSize = normalizePageSize(request.getPageSize());
        String knowledgeBaseId = normalizeKnowledgeBaseId(request.getKnowledgeBaseId());
        long version = fileCacheService.currentVersion(userId, knowledgeBaseId);
        PageResponse<FileViewResponse> cached = fileCacheService.getPage(userId, knowledgeBaseId, version, pageNum, pageSize);
        if (cached != null) {
            return mergeTemporaryItems(userId, pageNum, cached);
        }

        String lockKey = FileRedisKeys.libraryLockKey(userId, knowledgeBaseId);
        String lockToken = fileCacheService.tryLock(lockKey, Duration.ofSeconds(properties.getCache().getLockTtlSeconds()));
        if (lockToken == null) {
            PageResponse<FileViewResponse> waitedCache = waitForPageCache(userId, knowledgeBaseId, version, pageNum, pageSize);
            if (waitedCache != null) {
                return mergeTemporaryItems(userId, pageNum, waitedCache);
            }
            lockToken = fileCacheService.tryLock(lockKey, Duration.ofSeconds(properties.getCache().getLockTtlSeconds()));
            if (lockToken == null) {
                throw new IllegalStateException("文件列表正在刷新，请稍后重试");
            }
        }

        try {
            int offset = (pageNum - 1) * pageSize;
            List<FileViewResponse> records = documentFileMapper.selectPage(userId, knowledgeBaseId, offset, pageSize)
                    .stream()
                    .map(FileViewMapper::fromDocumentFile)
                    .toList();
            long total = documentFileMapper.countByUser(userId, knowledgeBaseId);
            PageResponse<FileViewResponse> page = PageResponse.of(records, total, pageNum, pageSize);
            fileCacheService.putPage(userId, knowledgeBaseId, version, pageNum, pageSize, page);
            return mergeTemporaryItems(userId, pageNum, page);
        } finally {
            fileCacheService.unlock(lockKey, lockToken);
        }
    }

    /**
     * 普通上传文件。
     */
    @Override
    public FileUploadResponse upload(Long userId, String knowledgeBaseId, MultipartFile file) {
        validateUploadFile(file, false);
        String normalizedKnowledgeBaseId = normalizeKnowledgeBaseId(knowledgeBaseId);
        String uploadId = FileIdGenerator.uploadId();
        String fileId = FileIdGenerator.fileId();
        String fileExt = FileTypeResolver.extension(file.getOriginalFilename());
        FileUploadSession session = createSession(userId, uploadId, fileId, normalizedKnowledgeBaseId, file, fileExt, 1);
        uploadSessionMapper.insert(session);
        saveTemporaryItem(userId, session, "UPLOADING", 1);

        try {
            String sha256 = HashUtils.sha256(file.getInputStream());
            StoredObject storedObject = objectStorageService.uploadOriginal(userId, fileId, fileExt, file);
            DocumentFile documentFile = createDocumentFile(session, storedObject, sha256);
            documentFileMapper.insert(documentFile);
            documentFileLookupService.cacheFile(documentFile);
            session.setFileSha256(sha256);
            session.setBucketName(storedObject.getBucketName());
            session.setObjectKey(storedObject.getObjectKey());
            session.setUploadedChunks(1);
            uploadSessionMapper.markUploaded(session);
            fileCacheService.removeUploadItem(userId, uploadId);
            fileCacheService.increaseVersion(userId, normalizedKnowledgeBaseId);
            return new FileUploadResponse(uploadId, FileViewMapper.fromDocumentFile(documentFile));
        } catch (Exception exception) {
            markUploadFailed(userId, uploadId, session, exception.getMessage());
            throw new IllegalStateException("文件上传失败", exception);
        }
    }

    /**
     * 初始化分片上传。
     */
    @Override
    public MultipartInitResponse initMultipart(Long userId, MultipartInitRequest request) {
        validateMultipartRequest(request);
        String uploadId = FileIdGenerator.uploadId();
        String fileId = FileIdGenerator.fileId();
        String fileExt = FileTypeResolver.extension(request.getFileName());
        long chunkSize = resolveMultipartChunkSize(request);
        int totalChunks = (int) Math.ceil(request.getFileSize() * 1.0 / chunkSize);

        FileUploadSession session = new FileUploadSession();
        session.setUploadId(uploadId);
        session.setUserId(userId);
        session.setFileId(fileId);
        session.setKnowledgeBaseId(normalizeKnowledgeBaseId(request.getKnowledgeBaseId()));
        session.setFileName(request.getFileName());
        session.setFileSize(request.getFileSize());
        session.setFileCategory(FileTypeResolver.category(request.getFileName()));
        session.setFileExt(fileExt);
        session.setMimeType(request.getMimeType());
        session.setFileSha256(request.getFileSha256());
        session.setChunkSize(chunkSize);
        session.setTotalChunks(totalChunks);
        session.setUploadedChunks(0);
        session.setStatus("PENDING_UPLOAD");
        session.setTempBucketName(properties.getMinio().getTempBucket());
        session.setTempPrefix("users/" + userId + "/multipart/" + uploadId + "/");
        session.setExpiresAt(LocalDateTime.now().plusHours(properties.getUpload().getSessionExpireHours()));
        uploadSessionMapper.insert(session);
        saveTemporaryItem(userId, session, "PENDING_UPLOAD", 0);
        return new MultipartInitResponse(uploadId, fileId, chunkSize, totalChunks, List.of());
    }

    /**
     * 上传单个分片。
     */
    @Override
    public UploadStatusResponse uploadChunk(Long userId, String uploadId, Integer chunkIndex, MultipartFile chunk) {
        FileUploadSession session = requireSession(userId, uploadId);
        if (chunkIndex == null || chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) {
            throw new IllegalArgumentException("分片序号不合法");
        }
        if (chunk == null || chunk.isEmpty()) {
            throw new IllegalArgumentException("分片不能为空");
        }
        validateChunkPayload(session, chunkIndex, chunk.getSize());
        try {
            StoredObject storedObject = objectStorageService.uploadTempChunk(userId, uploadId, chunkIndex, chunk);
            FileUploadChunk uploadChunk = new FileUploadChunk();
            uploadChunk.setUploadId(uploadId);
            uploadChunk.setChunkIndex(chunkIndex);
            uploadChunk.setChunkSize(chunk.getSize());
            uploadChunk.setChunkSha256(HashUtils.sha256(chunk.getInputStream()));
            uploadChunk.setBucketName(storedObject.getBucketName());
            uploadChunk.setObjectKey(storedObject.getObjectKey());
            uploadChunk.setStatus("UPLOADED");
            uploadChunkMapper.upsert(uploadChunk);
            int uploadedCount = uploadChunkMapper.countUploaded(uploadId);
            session.setStatus("UPLOADING");
            session.setUploadedChunks(uploadedCount);
            session.setErrorMessage(null);
            uploadSessionMapper.updateStatusAndChunks(session);
            saveTemporaryItem(userId, session, "UPLOADING", percent(uploadedCount, session.getTotalChunks()));
            return status(userId, uploadId);
        } catch (Exception exception) {
            markUploadFailed(userId, uploadId, session, exception.getMessage());
            throw new IllegalStateException("分片上传失败", exception);
        }
    }

    /**
     * 完成分片上传。
     */
    @Override
    public FileUploadResponse completeMultipart(Long userId, MultipartCompleteRequest request) {
        FileUploadSession session = requireSession(userId, request.getUploadId());
        String lockKey = FileRedisKeys.uploadCompleteLockKey(request.getUploadId());
        String lockToken = fileCacheService.tryLock(lockKey, Duration.ofMinutes(5));
        if (lockToken == null) {
            throw new IllegalArgumentException("文件正在合并，请勿重复提交");
        }
        try {
            if ("UPLOADED".equals(session.getStatus()) && StringUtils.hasText(session.getFileId())) {
                DocumentFile existed = documentFileLookupService.requireFile(userId, session.getFileId());
                return new FileUploadResponse(session.getUploadId(), FileViewMapper.fromDocumentFile(existed));
            }
            List<FileUploadChunk> chunks = uploadChunkMapper.findUploadedChunks(session.getUploadId());
            if (chunks.size() != session.getTotalChunks()) {
                throw new IllegalArgumentException("分片尚未全部上传完成");
            }
            session.setStatus("COMPLETING");
            session.setUploadedChunks(chunks.size());
            uploadSessionMapper.updateStatusAndChunks(session);
            saveTemporaryItem(userId, session, "COMPLETING", 99);

            StoredObject storedObject = objectStorageService.composeOriginal(userId, session.getFileId(), session.getFileExt(), chunks);
            String sha256 = StringUtils.hasText(request.getFileSha256()) ? request.getFileSha256() : session.getFileSha256();
            DocumentFile documentFile = createDocumentFile(session, storedObject, sha256);
            documentFileMapper.insert(documentFile);
            documentFileLookupService.cacheFile(documentFile);
            session.setBucketName(storedObject.getBucketName());
            session.setObjectKey(storedObject.getObjectKey());
            session.setUploadedChunks(chunks.size());
            uploadSessionMapper.markUploaded(session);
            objectStorageService.removeTempChunks(chunks);
            uploadChunkMapper.deleteByUploadId(session.getUploadId());
            fileCacheService.removeUploadItem(userId, session.getUploadId());
            fileCacheService.increaseVersion(userId, session.getKnowledgeBaseId());
            return new FileUploadResponse(session.getUploadId(), FileViewMapper.fromDocumentFile(documentFile));
        } catch (Exception exception) {
            markUploadFailed(userId, session.getUploadId(), session, exception.getMessage());
            throw exception;
        } finally {
            fileCacheService.unlock(lockKey, lockToken);
        }
    }

    /**
     * 查询上传会话状态。
     */
    @Override
    public UploadStatusResponse status(Long userId, String uploadId) {
        FileUploadSession session = requireSession(userId, uploadId);
        List<Integer> indexes = uploadChunkMapper.findUploadedIndexes(uploadId);
        return new UploadStatusResponse(
                uploadId,
                session.getStatus(),
                session.getChunkSize(),
                session.getUploadedChunks(),
                session.getTotalChunks(),
                indexes,
                session.getErrorMessage()
        );
    }

    /**
     * 取消上传会话。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long userId, String uploadId) {
        FileUploadSession session = requireSession(userId, uploadId);
        List<FileUploadChunk> chunks = uploadChunkMapper.findUploadedChunks(uploadId);
        objectStorageService.removeTempChunks(chunks);
        uploadChunkMapper.deleteByUploadId(uploadId);
        uploadSessionMapper.markCanceled(userId, uploadId);
        fileCacheService.removeUploadItem(userId, session.getUploadId());
    }

    /**
     * 丢弃失败上传项。
     */
    @Override
    public void discardFailed(Long userId, UploadFailureRequest request) {
        List<String> uploadIds = normalizeUploadIds(userId, request);
        uploadIds.forEach(uploadId -> cancel(userId, uploadId));
    }

    /**
     * 标记页面离开导致的上传中断，并清理失败项。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void interruptUploads(Long userId, UploadFailureRequest request) {
        List<FileUploadSession> sessions = resolveInterruptSessions(userId, request);
        sessions.forEach(session -> {
            if (uploadSessionMapper.markInterrupted(userId, session.getUploadId()) > 0) {
                session.setStatus("INTERRUPTED");
                session.setErrorMessage("上传已中断，可重新选择文件继续上传");
                saveTemporaryItem(userId, session, "INTERRUPTED", percent(session.getUploadedChunks(), session.getTotalChunks()));
            }
        });
        clearFailedUploads(userId);
    }

    /**
     * 清理当前用户失败上传项。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearFailedUploads(Long userId) {
        uploadSessionMapper.selectFailedByUser(userId)
                .forEach(session -> cancel(userId, session.getUploadId()));
    }

    /**
     * 生成失败上传项清单，前端据此重新排队上传原文件。
     */
    @Override
    public List<FileViewResponse> retryFailed(Long userId, UploadFailureRequest request) {
        return normalizeUploadIds(userId, request).stream()
                .map(uploadId -> requireSession(userId, uploadId))
                .map(session -> temporaryItem(session, "UPLOAD_FAILED", 0))
                .toList();
    }

    /**
     * 查询当前用户可恢复上传会话。
     */
    @Override
    public List<RecoverableUploadResponse> recoverableUploads(Long userId) {
        return uploadSessionMapper.selectRecoverableByUser(userId).stream()
                .map(session -> {
                    List<Integer> indexes = uploadChunkMapper.findUploadedIndexes(session.getUploadId());
                    FileViewResponse item = temporaryItem(session, session.getStatus(), percent(session.getUploadedChunks(), session.getTotalChunks()));
                    fileCacheService.saveUploadItem(userId, item);
                    return new RecoverableUploadResponse(
                            item,
                            session.getUploadId(),
                            session.getFileName(),
                            session.getFileSize(),
                            session.getChunkSize(),
                            session.getTotalChunks(),
                            session.getUploadedChunks(),
                            indexes,
                            session.getStatus(),
                            session.getErrorMessage()
                    );
                })
                .toList();
    }

    /**
     * 下载或预览文件。
     */
    @Override
    public ResponseEntity<InputStreamResource> download(Long userId, String fileId, boolean inline) {
        DocumentFile file = documentFileLookupService.requireFile(userId, fileId);
        InputStreamResource resource = new InputStreamResource(objectStorageService.getObject(file.getBucketName(), file.getObjectKey()));
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(file.getOriginalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /**
     * 删除文件。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, String fileId) {
        DocumentFile file = documentFileLookupService.requireFile(userId, fileId);
        documentFileMapper.softDelete(userId, fileId);
        objectStorageService.removeObject(file.getBucketName(), file.getObjectKey());
        fileCacheService.increaseVersion(userId, file.getKnowledgeBaseId());
        file.setDeleted(1);
        file.setUploadStatus("DELETED");
        documentFileLookupService.cacheFileAfterCommit(file);
    }

    /**
     * 用户手动提交解析请求。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reindex(Long userId, String fileId) {
        String lockKey = FileRedisKeys.manualParseLockKey(userId, fileId);
        String lockToken = fileCacheService.tryLock(lockKey, Duration.ofSeconds(30));
        if (lockToken == null) {
            throw new IllegalArgumentException("该文档解析请求正在提交，请稍后再试");
        }
        try {
            DocumentFile file = documentFileLookupService.requireFile(userId, fileId);

            String parseStatus = normalizedParseStatus(file.getParseStatus());
            if ("PENDING".equals(parseStatus) || "PROCESSING".equals(parseStatus)) {
                throw new IllegalArgumentException("文档正在解析中，请勿重复提交");
            }
            if ("SUCCESS".equals(parseStatus)) {
                throw new IllegalArgumentException("文档已解析完成，无需重复解析");
            }

            boolean reparse = "FAILED".equals(parseStatus);
            int currentRetryCount = normalizedRetryCount(file.getParseRetryCount());
            if (reparse && currentRetryCount >= MAX_MANUAL_REPARSE_COUNT) {
                throw new IllegalArgumentException("请稍后再试");
            }

            int nextRetryCount = reparse ? currentRetryCount + 1 : 0;
            int updatedRows = documentFileMapper.markParseRequested(userId, fileId, parseStatus, nextRetryCount);
            if (updatedRows == 0) {
                throw new IllegalArgumentException("解析状态已变化，请刷新后再试");
            }

            createParseTask(userId, fileId, reparse);
            String eventId = FileIdGenerator.compactUuid();
            sendParseEvent(file, reparse ? "MANUAL_REPARSE" : "MANUAL_PARSE", eventId);
            fileCacheService.increaseVersion(userId, file.getKnowledgeBaseId());
            file.setParseStatus("PENDING");
            file.setIndexStatus("NONE");
            file.setGraphStatus("NONE");
            file.setSummary(null);
            file.setKeywordsJson(null);
            file.setErrorMessage(null);
            file.setParseRetryCount(nextRetryCount);
            documentFileLookupService.cacheFileAfterCommit(file);
        } finally {
            fileCacheService.unlock(lockKey, lockToken);
        }
    }

    /**
     * 内部解析服务回写解析结果。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateParseResult(DocumentParseCallbackRequest request) {
        String parseStatus = normalizedCallbackParseStatus(request.getParseStatus());
        DocumentFile file = documentFileLookupService.requireFile(request.getUserId(), request.getFileId());

        String summary = "SUCCESS".equals(parseStatus) ? request.getSummary() : null;
        String keywordsJson = "SUCCESS".equals(parseStatus) ? request.getKeywordsJson() : null;
        String errorMessage = "FAILED".equals(parseStatus)
                ? defaultIfBlank(request.getErrorMessage(), "解析失败，请稍后重试")
                : null;
        int updatedRows = documentFileMapper.updateParseResult(
                request.getUserId(),
                request.getFileId(),
                parseStatus,
                "NONE",
                "NONE",
                summary,
                keywordsJson,
                errorMessage
        );
        if (updatedRows == 0) {
            throw new IllegalArgumentException("解析结果回写失败，请检查文件状态");
        }
        if (StringUtils.hasText(request.getTaskId())) {
            processTaskMapper.updateTaskStatus(
                    request.getTaskId(),
                    request.getUserId(),
                    request.getFileId(),
                    "SUCCESS".equals(parseStatus) ? "SUCCESS" : "FAILED",
                    "SUCCESS".equals(parseStatus) ? "解析完成" : "解析失败",
                    "SUCCESS".equals(parseStatus) ? 100 : 0
            );
        }
        fileCacheService.increaseVersion(request.getUserId(), file.getKnowledgeBaseId());
        file.setParseStatus(parseStatus);
        file.setIndexStatus("NONE");
        file.setGraphStatus("NONE");
        file.setSummary(summary);
        file.setKeywordsJson(keywordsJson);
        file.setErrorMessage(errorMessage);
        documentFileLookupService.cacheFileAfterCommit(file);
    }

    /**
     * 创建上传会话对象。
     */
    private FileUploadSession createSession(Long userId,
                                            String uploadId,
                                            String fileId,
                                            String knowledgeBaseId,
                                            MultipartFile file,
                                            String fileExt,
                                            int totalChunks) {
        FileUploadSession session = new FileUploadSession();
        session.setUploadId(uploadId);
        session.setUserId(userId);
        session.setFileId(fileId);
        session.setKnowledgeBaseId(knowledgeBaseId);
        session.setFileName(file.getOriginalFilename());
        session.setFileSize(file.getSize());
        session.setFileCategory(FileTypeResolver.category(file.getOriginalFilename()));
        session.setFileExt(fileExt);
        session.setMimeType(file.getContentType());
        session.setChunkSize(properties.getUpload().getChunkSizeBytes());
        session.setTotalChunks(totalChunks);
        session.setUploadedChunks(0);
        session.setStatus("PENDING_UPLOAD");
        session.setExpiresAt(LocalDateTime.now().plusHours(properties.getUpload().getSessionExpireHours()));
        return session;
    }

    /**
     * 创建正式文件元数据对象。
     */
    private DocumentFile createDocumentFile(FileUploadSession session, StoredObject storedObject, String sha256) {
        DocumentFile documentFile = new DocumentFile();
        documentFile.setFileId(session.getFileId());
        documentFile.setUserId(session.getUserId());
        documentFile.setKnowledgeBaseId(session.getKnowledgeBaseId());
        documentFile.setOriginalName(session.getFileName());
        documentFile.setFileCategory(session.getFileCategory());
        documentFile.setFileExt(session.getFileExt());
        documentFile.setMimeType(session.getMimeType());
        documentFile.setFileSize(session.getFileSize());
        documentFile.setFileSha256(sha256);
        documentFile.setStorageType("MINIO");
        documentFile.setBucketName(storedObject.getBucketName());
        documentFile.setObjectKey(storedObject.getObjectKey());
        documentFile.setUploadStatus("UPLOADED");
        documentFile.setParseStatus("NOT_REQUESTED");
        documentFile.setIndexStatus("NONE");
        documentFile.setGraphStatus("NONE");
        documentFile.setParseRetryCount(0);
        documentFile.setCurrentVersion(1);
        documentFile.setEditable(Set.of("txt", "docx", "pptx").contains(session.getFileExt()) ? 1 : 0);
        documentFile.setContentHash(null);
        documentFile.setDeleted(0);
        documentFile.setCreatedAt(LocalDateTime.now());
        return documentFile;
    }

    /**
     * 创建等待解析任务，任务进入 WAITING 后由 MQ 消费者排队处理。
     */
    private void createParseTask(Long userId, String fileId, boolean reparse) {
        DocumentProcessTask task = new DocumentProcessTask();
        task.setTaskId(FileIdGenerator.taskId());
        task.setFileId(fileId);
        task.setUserId(userId);
        task.setTaskType("PARSE_DOCUMENT");
        task.setTaskStatus("WAITING");
        task.setStage(reparse ? "等待重新解析" : "等待解析");
        task.setProgress(0);
        processTaskMapper.insert(task);
    }

    /**
     * 投递用户手动解析 MQ 事件，多个不同文档的解析请求由 RocketMQ 队列自然排队。
     */
    private void sendParseEvent(DocumentFile file, String reason, String eventId) {
        RocketMQTemplate rocketMQTemplate = rocketMQTemplateProvider.getIfAvailable();
        if (rocketMQTemplate == null) {
            log.warn("RocketMQTemplate 不可用，解析任务已保留 WAITING，fileId={}, eventId={}", file.getFileId(), eventId);
            return;
        }
        DocumentReparseEvent event = new DocumentReparseEvent(
                eventId,
                file.getFileId(),
                file.getUserId(),
                normalizedVersion(file),
                file.getBucketName(),
                file.getObjectKey(),
                reason,
                LocalDateTime.now()
        );
        String destination = MqTopicConstants.FILE_EVENT_TOPIC + ":" + MqTopicConstants.TAG_DOCUMENT_REPARSE_REQUESTED;
        try {
            rocketMQTemplate.syncSendOrderly(destination, event, String.valueOf(file.getUserId()));
        } catch (Exception exception) {
            log.warn("发送用户手动解析事件失败，fileId={}, eventId={}", file.getFileId(), eventId, exception);
        }
    }

    /**
     * 合并 Redis 临时上传项。
     */
    private PageResponse<FileViewResponse> mergeTemporaryItems(Long userId, int pageNum, PageResponse<FileViewResponse> page) {
        if (pageNum != 1) {
            return page;
        }
        List<FileViewResponse> temporaryItems = fileCacheService.listUploadItems(userId);
        List<FileViewResponse> restoredItems = restoreTemporaryItemsFromDatabase(userId, temporaryItems);
        if (restoredItems.isEmpty()) {
            return page;
        }
        List<FileViewResponse> merged = new ArrayList<>(restoredItems);
        merged.addAll(page.getRecords());
        return PageResponse.of(merged, page.getTotal() + restoredItems.size(), page.getPageNum(), page.getPageSize());
    }

    /**
     * 等待持有回源锁的线程完成缓存回填，避免缓存击穿时大量请求直接打到 MySQL。
     */
    private PageResponse<FileViewResponse> waitForPageCache(Long userId,
                                                            String knowledgeBaseId,
                                                            long version,
                                                            int pageNum,
                                                            int pageSize) {
        int attempts = Math.max(1, properties.getCache().getLockWaitAttempts());
        for (int i = 0; i < attempts; i++) {
            fileCacheService.shortWait();
            PageResponse<FileViewResponse> cached = fileCacheService.getPage(userId, knowledgeBaseId, version, pageNum, pageSize);
            if (cached != null) {
                return cached;
            }
        }
        return null;
    }

    /**
     * 保存上传临时展示项。
     */
    private void saveTemporaryItem(Long userId, FileUploadSession session, String status, int progress) {
        fileCacheService.saveUploadItem(userId, temporaryItem(session, status, progress));
    }

    /**
     * 创建上传临时展示项。
     */
    private FileViewResponse temporaryItem(FileUploadSession session, String status, int progress) {
        FileViewResponse item = new FileViewResponse();
        item.setId(session.getFileId());
        item.setFileId(session.getFileId());
        item.setUploadId(session.getUploadId());
        item.setName(session.getFileName());
        item.setType(FileTypeResolver.shortType(session.getFileName()));
        item.setFileExt(session.getFileExt());
        item.setFileSize(session.getFileSize());
        item.setSizeText(FileViewMapper.formatSize(session.getFileSize()));
        item.setTimeText("刚刚");
        item.setUploadStatus(status);
        item.setParseStatus("NOT_REQUESTED");
        item.setStatusText(switch (status) {
            case "PENDING_UPLOAD" -> "待上传";
            case "UPLOADING" -> "上传中";
            case "COMPLETING" -> "合并中";
            case "INTERRUPTED" -> "上传中断";
            case "UPLOAD_FAILED" -> "上传失败";
            default -> "待上传";
        });
        item.setStatusTone(switch (status) {
            case "UPLOAD_FAILED" -> "red";
            case "INTERRUPTED" -> "amber";
            default -> "blue";
        });
        item.setKnowledgeText("未发起解析");
        item.setKnowledgeTone("idle");
        item.setGraphText("未发起解析");
        item.setGraphTone("idle");
        item.setProgress(progress);
        item.setErrorMessage(session.getErrorMessage());
        return item;
    }

    /**
     * 标记上传失败并保留 Redis 临时项。
     */
    private void markUploadFailed(Long userId, String uploadId, FileUploadSession session, String errorMessage) {
        String message = StringUtils.hasText(errorMessage) ? errorMessage : "上传失败，请稍后再试";
        session.setStatus("UPLOAD_FAILED");
        session.setErrorMessage(message);
        uploadFailureService.recordFailed(userId, session, temporaryItem(session, "UPLOAD_FAILED", 0), message);
    }

    /**
     * 查询当前用户上传会话，不存在时抛业务异常。
     */
    private FileUploadSession requireSession(Long userId, String uploadId) {
        FileUploadSession session = uploadSessionMapper.selectByUserAndUploadId(userId, uploadId);
        if (session == null) {
            throw new IllegalArgumentException("上传会话不存在或无权访问");
        }
        return session;
    }

    /**
     * 校验普通上传文件。
     */
    private void validateUploadFile(MultipartFile file, boolean multipartOnly) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (file.getSize() > properties.getUpload().getMaxSizeBytes()) {
            throw new IllegalArgumentException("单个文件不能超过 200MB");
        }
        if (!multipartOnly && file.getSize() >= MIN_MULTIPART_SIZE_BYTES) {
            throw new IllegalArgumentException("5MB 及以上文件请使用分片上传");
        }
        if (multipartOnly && file.getSize() < MIN_MULTIPART_SIZE_BYTES) {
            throw new IllegalArgumentException("该文件不需要分片上传");
        }
        String extension = FileTypeResolver.extension(file.getOriginalFilename());
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("仅支持 PDF、Word、PPT、WPS/WPD 和 TXT 文件，不支持图片、视频或表格文件");
        }
    }

    /**
     * 校验分片初始化请求。
     */
    private void validateMultipartRequest(MultipartInitRequest request) {
        if (request.getFileSize() > properties.getUpload().getMaxSizeBytes()) {
            throw new IllegalArgumentException("单个文件不能超过 200MB");
        }
        if (request.getFileSize() < MIN_MULTIPART_SIZE_BYTES) {
            throw new IllegalArgumentException("小于 5MB 的文件请使用普通上传");
        }
        String extension = FileTypeResolver.extension(request.getFileName());
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("仅支持 PDF、Word、PPT、WPS/WPD 和 TXT 文件，不支持图片、视频或表格文件");
        }
    }

    /**
     * 解析并校验分片大小，确保新上传不会再生成小于 MinIO compose 限制的非末尾分片。
     */
    private long resolveMultipartChunkSize(MultipartInitRequest request) {
        long defaultChunkSize = Math.max(properties.getUpload().getChunkSizeBytes(), MIN_CHUNK_SIZE_BYTES);
        long chunkSize = request.getChunkSize() == null || request.getChunkSize() <= 0
                ? defaultChunkSize
                : request.getChunkSize();
        if (chunkSize < MIN_CHUNK_SIZE_BYTES || chunkSize > MAX_CHUNK_SIZE_BYTES) {
            throw new IllegalArgumentException("分片大小不合法，单片必须在 5MB 到 10MB 之间");
        }
        return chunkSize;
    }

    /**
     * 校验实际上传的分片大小，避免客户端传入小分片导致 MinIO 合并失败或最终文件错位。
     */
    private void validateChunkPayload(FileUploadSession session, Integer chunkIndex, long actualSize) {
        long chunkSize = session.getChunkSize() == null || session.getChunkSize() <= 0
                ? Math.max(properties.getUpload().getChunkSizeBytes(), MIN_CHUNK_SIZE_BYTES)
                : session.getChunkSize();
        boolean lastChunk = chunkIndex == session.getTotalChunks() - 1;
        long expectedSize = lastChunk
                ? session.getFileSize() - (long) chunkIndex * chunkSize
                : chunkSize;
        if (actualSize != expectedSize) {
            throw new IllegalArgumentException("分片大小与上传会话不一致，请重新上传该文件");
        }
        if (!lastChunk && actualSize < MIN_CHUNK_SIZE_BYTES) {
            throw new IllegalArgumentException("非末尾分片不能小于 5MB");
        }
    }

    /**
     * 规范化解析状态，空值按未发起解析处理。
     */
    private String normalizedParseStatus(String parseStatus) {
        return StringUtils.hasText(parseStatus) ? parseStatus.trim().toUpperCase() : "NOT_REQUESTED";
    }

    /**
     * 校验解析回调状态，只允许成功或失败回调改最终态。
     */
    private String normalizedCallbackParseStatus(String parseStatus) {
        String normalized = normalizedParseStatus(parseStatus);
        if (!"SUCCESS".equals(normalized) && !"FAILED".equals(normalized)) {
            throw new IllegalArgumentException("解析回调状态只允许 SUCCESS 或 FAILED");
        }
        return normalized;
    }

    /**
     * 为空时返回默认文案。
     */
    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    /**
     * 规范化用户手动重新解析次数。
     */
    private int normalizedRetryCount(Integer retryCount) {
        return retryCount == null || retryCount < 0 ? 0 : retryCount;
    }

    /**
     * 获取当前文件版本号，缺失时按第一版处理。
     */
    private int normalizedVersion(DocumentFile file) {
        return file.getCurrentVersion() == null || file.getCurrentVersion() < 1 ? 1 : file.getCurrentVersion();
    }

    /**
     * 规范化页码。
     */
    private int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    /**
     * 规范化每页条数。
     */
    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 50);
    }

    /**
     * 规范化知识库 ID。
     */
    private String normalizeKnowledgeBaseId(String knowledgeBaseId) {
        String normalized = StringUtils.hasText(knowledgeBaseId) ? knowledgeBaseId.trim() : "default";
        if (normalized.length() > 64 || !normalized.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("知识库 ID 不合法");
        }
        return normalized;
    }

    /**
     * 计算上传进度。
     */
    private int percent(int uploadedChunks, int totalChunks) {
        if (totalChunks <= 0) {
            return 0;
        }
        return Math.min(99, (int) Math.round(uploadedChunks * 100.0 / totalChunks));
    }

    /**
     * 规范化失败上传 ID 列表。
     */
    private List<String> normalizeUploadIds(Long userId, UploadFailureRequest request) {
        if (request != null && request.getUploadIds() != null && !request.getUploadIds().isEmpty()) {
            return request.getUploadIds();
        }
        return uploadSessionMapper.selectFailedByUser(userId).stream()
                .map(FileUploadSession::getUploadId)
                .toList();
    }

    /**
     * 从 MySQL 恢复 Redis 丢失的上传队列项。
     */
    private List<FileViewResponse> restoreTemporaryItemsFromDatabase(Long userId, List<FileViewResponse> cachedItems) {
        List<FileViewResponse> items = new ArrayList<>(cachedItems);
        Set<String> existedUploadIds = new HashSet<>();
        cachedItems.forEach(item -> {
            if (StringUtils.hasText(item.getUploadId())) {
                existedUploadIds.add(item.getUploadId());
            }
        });

        List<FileUploadSession> sessions = new ArrayList<>();
        sessions.addAll(uploadSessionMapper.selectRecoverableByUser(userId));
        sessions.addAll(uploadSessionMapper.selectFailedByUser(userId));
        sessions.forEach(session -> {
            if (existedUploadIds.contains(session.getUploadId())) {
                return;
            }
            FileViewResponse item = temporaryItem(session, session.getStatus(), percent(session.getUploadedChunks(), session.getTotalChunks()));
            fileCacheService.saveUploadItem(userId, item);
            items.add(item);
        });
        return items;
    }

    /**
     * 解析需要标记为中断的上传会话。
     */
    private List<FileUploadSession> resolveInterruptSessions(Long userId, UploadFailureRequest request) {
        if (request == null || request.getUploadIds() == null || request.getUploadIds().isEmpty()) {
            return uploadSessionMapper.selectInterruptibleByUser(userId);
        }
        return request.getUploadIds().stream()
                .map(uploadId -> uploadSessionMapper.selectByUserAndUploadId(userId, uploadId))
                .filter(session -> session != null && Set.of("PENDING_UPLOAD", "UPLOADING", "COMPLETING").contains(session.getStatus()))
                .toList();
    }
}
