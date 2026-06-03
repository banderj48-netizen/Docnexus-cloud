package com.xyf.docnexus.file.service.impl;

import com.xyf.docnexus.common.VO.PageResponse;
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
import com.xyf.docnexus.file.service.FileService;
import com.xyf.docnexus.file.service.FileUploadFailureService;
import com.xyf.docnexus.file.service.ObjectStorageService;
import com.xyf.docnexus.file.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt",
            "jpg", "jpeg", "png", "webp", "bmp", "tif", "tiff", "gif"
    );

    private final DocumentFileMapper documentFileMapper;
    private final FileUploadSessionMapper uploadSessionMapper;
    private final FileUploadChunkMapper uploadChunkMapper;
    private final DocumentProcessTaskMapper processTaskMapper;
    private final ObjectStorageService objectStorageService;
    private final FileCacheService fileCacheService;
    private final FileUploadFailureService uploadFailureService;
    private final FileServiceProperties properties;

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
            createParseTask(userId, fileId);
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
        long chunkSize = request.getChunkSize() == null || request.getChunkSize() <= 0
                ? properties.getUpload().getChunkSizeBytes()
                : request.getChunkSize();
        int totalChunks = request.getTotalChunks() == null || request.getTotalChunks() <= 0
                ? (int) Math.ceil(request.getFileSize() * 1.0 / chunkSize)
                : request.getTotalChunks();

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
                DocumentFile existed = documentFileMapper.selectByUserAndFileId(userId, session.getFileId());
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
            createParseTask(userId, session.getFileId());
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
        DocumentFile file = documentFileMapper.selectByUserAndFileId(userId, fileId);
        if (file == null) {
            throw new IllegalArgumentException("文件不存在或无权访问");
        }
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
        DocumentFile file = documentFileMapper.selectByUserAndFileId(userId, fileId);
        if (file == null) {
            throw new IllegalArgumentException("文件不存在或无权删除");
        }
        documentFileMapper.softDelete(userId, fileId);
        objectStorageService.removeObject(file.getBucketName(), file.getObjectKey());
        fileCacheService.increaseVersion(userId, file.getKnowledgeBaseId());
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
        documentFile.setParseStatus("PENDING");
        documentFile.setIndexStatus("NONE");
        documentFile.setGraphStatus("NONE");
        documentFile.setDeleted(0);
        documentFile.setCreatedAt(LocalDateTime.now());
        return documentFile;
    }

    /**
     * 创建等待解析任务。
     */
    private void createParseTask(Long userId, String fileId) {
        DocumentProcessTask task = new DocumentProcessTask();
        task.setTaskId(FileIdGenerator.taskId());
        task.setFileId(fileId);
        task.setUserId(userId);
        task.setTaskType("PARSE_DOCUMENT");
        task.setTaskStatus("WAITING");
        task.setStage("等待解析");
        task.setProgress(0);
        processTaskMapper.insert(task);
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
        item.setParseStatus("PENDING");
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
        item.setKnowledgeText("待解析");
        item.setKnowledgeTone("waiting");
        item.setGraphText("待解析");
        item.setGraphTone("waiting");
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
        if (multipartOnly && file.getSize() <= properties.getUpload().getMultipartThresholdBytes()) {
            throw new IllegalArgumentException("该文件不需要分片上传");
        }
        String extension = FileTypeResolver.extension(file.getOriginalFilename());
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("仅支持 PDF、Word、PPT、Excel、TXT 和常见图片格式");
        }
    }

    /**
     * 校验分片初始化请求。
     */
    private void validateMultipartRequest(MultipartInitRequest request) {
        if (request.getFileSize() > properties.getUpload().getMaxSizeBytes()) {
            throw new IllegalArgumentException("单个文件不能超过 200MB");
        }
        if (request.getFileSize() <= properties.getUpload().getMultipartThresholdBytes()) {
            throw new IllegalArgumentException("小于等于 100MB 的文件请使用普通上传");
        }
        String extension = FileTypeResolver.extension(request.getFileName());
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("仅支持 PDF、Word、PPT、Excel、TXT 和常见图片格式");
        }
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
