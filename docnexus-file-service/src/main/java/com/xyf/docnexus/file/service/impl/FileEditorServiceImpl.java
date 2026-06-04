package com.xyf.docnexus.file.service.impl;

import com.xyf.docnexus.common.constant.MqTopicConstants;
import com.xyf.docnexus.file.dto.*;
import com.xyf.docnexus.file.entity.DocumentContentRevisionLog;
import com.xyf.docnexus.file.entity.DocumentFile;
import com.xyf.docnexus.file.entity.DocumentFileContent;
import com.xyf.docnexus.file.entity.DocumentProcessTask;
import com.xyf.docnexus.file.mapper.DocumentContentRevisionLogMapper;
import com.xyf.docnexus.file.mapper.DocumentFileContentMapper;
import com.xyf.docnexus.file.mapper.DocumentFileMapper;
import com.xyf.docnexus.file.mapper.DocumentProcessTaskMapper;
import com.xyf.docnexus.file.service.*;
import com.xyf.docnexus.file.util.DocumentHtmlSanitizer;
import com.xyf.docnexus.file.util.FileIdGenerator;
import com.xyf.docnexus.file.util.FileRedisKeys;
import com.xyf.docnexus.file.util.HashUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 文件在线编辑服务实现。
 */
@Slf4j
@Service
public class FileEditorServiceImpl implements FileEditorService {

    private static final Set<String> PDF_EXTENSIONS = Set.of("pdf");

    private final DocumentFileMapper documentFileMapper;
    private final DocumentFileContentMapper contentMapper;
    private final DocumentContentRevisionLogMapper revisionLogMapper;
    private final DocumentProcessTaskMapper processTaskMapper;
    private final ObjectStorageService objectStorageService;
    private final DocumentContentConverter contentConverter;
    private final EditorFileRenderer editorFileRenderer;
    private final FileCacheService fileCacheService;
    private final DocumentFileLookupService documentFileLookupService;
    private final RocketMQTemplate rocketMQTemplate;

    public FileEditorServiceImpl(DocumentFileMapper documentFileMapper,
                                 DocumentFileContentMapper contentMapper,
                                 DocumentContentRevisionLogMapper revisionLogMapper,
                                 DocumentProcessTaskMapper processTaskMapper,
                                 ObjectStorageService objectStorageService,
                                 DocumentContentConverter contentConverter,
                                 EditorFileRenderer editorFileRenderer,
                                 FileCacheService fileCacheService,
                                 DocumentFileLookupService documentFileLookupService,
                                 ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider) {
        this.documentFileMapper = documentFileMapper;
        this.contentMapper = contentMapper;
        this.revisionLogMapper = revisionLogMapper;
        this.processTaskMapper = processTaskMapper;
        this.objectStorageService = objectStorageService;
        this.contentConverter = contentConverter;
        this.editorFileRenderer = editorFileRenderer;
        this.fileCacheService = fileCacheService;
        this.documentFileLookupService = documentFileLookupService;
        this.rocketMQTemplate = rocketMQTemplateProvider.getIfAvailable();
    }

    /**
     * 打开文件编辑页。
     */
    @Override
    public FileEditorResponse openEditor(Long userId, String fileId) {
        DocumentFile file = documentFileLookupService.requireFile(userId, fileId);
        int currentVersion = normalizedVersion(file);
        String fileExt = normalizedExt(file);
        if (PDF_EXTENSIONS.contains(fileExt)) {
            return new FileEditorResponse(
                    file.getFileId(),
                    file.getOriginalName(),
                    file.getFileExt(),
                    false,
                    currentVersion,
                    "",
                    "/api/files/preview/" + file.getFileId(),
                    file.getContentHash() == null ? file.getFileSha256() : file.getContentHash(),
                    file.getParseStatus(),
                    file.getIndexStatus(),
                    file.getUpdatedAt()
            );
        }

        DocumentFileContent cached = contentMapper.selectByFileAndVersion(userId, fileId, currentVersion);
        if (cached == null || !file.getObjectKey().equals(cached.getSourceObjectKey())) {
            DocumentContentConversion conversion = contentConverter.convert(
                    file.getFileExt(),
                    objectStorageService.getObject(file.getBucketName(), file.getObjectKey())
            );
            cached = buildContent(file, currentVersion, conversion);
            contentMapper.upsert(cached);
            documentFileMapper.updateEditorSnapshot(userId, fileId, conversion.isEditable() ? 1 : 0, conversion.getContentHash());
            file.setEditable(conversion.isEditable() ? 1 : 0);
            file.setContentHash(conversion.getContentHash());
            documentFileLookupService.cacheFile(file);
        }

        return new FileEditorResponse(
                file.getFileId(),
                file.getOriginalName(),
                file.getFileExt(),
                file.getEditable() != null && file.getEditable() == 1,
                currentVersion,
                cached.getContentHtml(),
                "",
                cached.getContentHash(),
                file.getParseStatus(),
                file.getIndexStatus(),
                file.getUpdatedAt()
        );
    }

    /**
     * 保存在线编辑内容并触发重新解析。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileEditorSaveResponse saveContent(Long userId, String fileId, FileEditorSaveRequest request) {
        String lockKey = FileRedisKeys.editorSaveLockKey(fileId);
        String lockToken = fileCacheService.tryLock(lockKey, Duration.ofMinutes(3));
        if (lockToken == null) {
            throw new IllegalArgumentException("文档正在保存，请稍后再试");
        }

        try {
            DocumentFile file = documentFileLookupService.requireFile(userId, fileId);
            if (!contentConverter.editable(file.getFileExt())) {
                throw new IllegalArgumentException("当前文件格式暂不支持在线保存");
            }
            int currentVersion = normalizedVersion(file);
            if (!Integer.valueOf(currentVersion).equals(request.getCurrentVersion())) {
                throw new IllegalArgumentException("文档已被更新，请刷新后再编辑");
            }

            String safeHtml = DocumentHtmlSanitizer.sanitize(request.getContentHtml());
            String contentHash = HashUtils.sha256(safeHtml);
            if (contentHash.equals(file.getContentHash())) {
                return new FileEditorSaveResponse(fileId, currentVersion, contentHash, file.getParseStatus(), file.getIndexStatus());
            }

            int newVersion = currentVersion + 1;
            byte[] bytes = editorFileRenderer.render(file.getFileExt(), safeHtml);
            String fileSha256 = HashUtils.sha256(new java.io.ByteArrayInputStream(bytes));
            StoredObject storedObject = new StoredObject(file.getBucketName(), file.getObjectKey());
            int updatedRows = documentFileMapper.updateEditorObjectByVersion(
                    userId,
                    fileId,
                    currentVersion,
                    file.getBucketName(),
                    file.getObjectKey(),
                    (long) bytes.length,
                    fileSha256,
                    contentHash
            );
            if (updatedRows == 0) {
                throw new IllegalArgumentException("文档已被更新，请刷新后再编辑");
            }

            DocumentFileContent content = new DocumentFileContent();
            content.setFileId(fileId);
            content.setUserId(userId);
            content.setVersionNumber(newVersion);
            content.setContentFormat("HTML");
            content.setContentHtml(safeHtml);
            content.setPlainText(toPlainText(safeHtml));
            content.setContentHash(contentHash);
            content.setSourceBucket(storedObject.getBucketName());
            content.setSourceObjectKey(storedObject.getObjectKey());
            contentMapper.upsert(content);

            String eventId = FileIdGenerator.compactUuid();
            saveRevisionLog(file, storedObject, currentVersion, newVersion, contentHash, eventId);
            createParseTask(userId, fileId);
            objectStorageService.overwriteObject(
                    file.getBucketName(),
                    file.getObjectKey(),
                    bytes,
                    editorFileRenderer.contentType(file.getFileExt())
            );
            fileCacheService.increaseVersion(userId, file.getKnowledgeBaseId());
            sendReparseEvent(file, storedObject, newVersion, eventId);
            refreshSavedFileCache(file, bytes.length, fileSha256, contentHash, newVersion);
            return new FileEditorSaveResponse(fileId, newVersion, contentHash, "PENDING", "NONE");
        } finally {
            fileCacheService.unlock(lockKey, lockToken);
        }
    }

    /**
     * 创建内容快照对象。
     */
    private DocumentFileContent buildContent(DocumentFile file, int currentVersion, DocumentContentConversion conversion) {
        DocumentFileContent content = new DocumentFileContent();
        content.setFileId(file.getFileId());
        content.setUserId(file.getUserId());
        content.setVersionNumber(currentVersion);
        content.setContentFormat(conversion.getContentFormat());
        content.setContentHtml(conversion.getContentHtml());
        content.setPlainText(conversion.getPlainText());
        content.setContentHash(conversion.getContentHash());
        content.setSourceBucket(file.getBucketName());
        content.setSourceObjectKey(file.getObjectKey());
        return content;
    }

    /**
     * 保存版本覆盖日志。
     */
    private void saveRevisionLog(DocumentFile file,
                                 StoredObject storedObject,
                                 int oldVersion,
                                 int newVersion,
                                 String contentHash,
                                 String eventId) {
        DocumentContentRevisionLog revisionLog = new DocumentContentRevisionLog();
        revisionLog.setEventId(eventId);
        revisionLog.setFileId(file.getFileId());
        revisionLog.setUserId(file.getUserId());
        revisionLog.setOldVersion(oldVersion);
        revisionLog.setNewVersion(newVersion);
        revisionLog.setOldObjectKey(file.getObjectKey());
        revisionLog.setNewObjectKey(storedObject.getObjectKey());
        revisionLog.setContentHash(contentHash);
        revisionLogMapper.insert(revisionLog);
    }

    /**
     * 保存成功后刷新单文件元数据缓存，避免在线用户继续读取旧版本号和旧 hash。
     */
    private void refreshSavedFileCache(DocumentFile file, long fileSize, String fileSha256, String contentHash, int newVersion) {
        file.setFileSize(fileSize);
        file.setFileSha256(fileSha256);
        file.setContentHash(contentHash);
        file.setCurrentVersion(newVersion);
        file.setParseStatus("PENDING");
        file.setIndexStatus("NONE");
        file.setGraphStatus("NONE");
        file.setSummary(null);
        file.setKeywordsJson(null);
        file.setErrorMessage(null);
        file.setParseRetryCount(0);
        file.setLastSavedAt(LocalDateTime.now());
        documentFileLookupService.cacheFileAfterCommit(file);
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
        task.setStage("等待重新解析");
        task.setProgress(0);
        processTaskMapper.insert(task);
    }

    /**
     * 发送重新解析事件。
     */
    private void sendReparseEvent(DocumentFile file, StoredObject storedObject, int newVersion, String eventId) {
        if (rocketMQTemplate == null) {
            log.warn("RocketMQTemplate 不可用，跳过重新解析事件发送，fileId={}, eventId={}", file.getFileId(), eventId);
            return;
        }
        DocumentReparseEvent event = new DocumentReparseEvent(
                eventId,
                file.getFileId(),
                file.getUserId(),
                newVersion,
                storedObject.getBucketName(),
                storedObject.getObjectKey(),
                "EDITOR_SAVE",
                LocalDateTime.now()
        );
        String destination = MqTopicConstants.FILE_EVENT_TOPIC + ":" + MqTopicConstants.TAG_DOCUMENT_REPARSE_REQUESTED;
        try {
            rocketMQTemplate.convertAndSend(destination, event);
        } catch (Exception exception) {
            log.warn("发送文档重新解析事件失败，fileId={}, eventId={}", file.getFileId(), eventId, exception);
        }
    }

    /**
     * 将 HTML 转为纯文本，便于后续统计和检索预留。
     */
    private String toPlainText(String html) {
        return DocumentHtmlSanitizer.sanitize(html)
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .trim();
    }

    /**
     * 获取有效版本号。
     */
    private int normalizedVersion(DocumentFile file) {
        return file.getCurrentVersion() == null || file.getCurrentVersion() < 1 ? 1 : file.getCurrentVersion();
    }

    /**
     * 获取小写扩展名。
     */
    private String normalizedExt(DocumentFile file) {
        return file.getFileExt() == null ? "" : file.getFileExt().toLowerCase();
    }
}
