package com.xyf.docnexus.file.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyf.docnexus.common.VO.PageResponse;
import com.xyf.docnexus.common.constant.MqTopicConstants;
import com.xyf.docnexus.file.config.FileServiceProperties;
import com.xyf.docnexus.file.dto.*;
import com.xyf.docnexus.file.entity.DocumentMetadata;
import com.xyf.docnexus.file.entity.DocumentFile;
import com.xyf.docnexus.file.entity.DocumentProcessTask;
import com.xyf.docnexus.file.entity.FileUploadChunk;
import com.xyf.docnexus.file.entity.FileUploadSession;
import com.xyf.docnexus.file.mapper.DocumentFileMapper;
import com.xyf.docnexus.file.mapper.DocumentMetadataMapper;
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
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private static final String DEFAULT_SPACE_CODE = "personal";
    private static final String DEFAULT_SPACE_NAME = "个人资料库";
    private static final String DEFAULT_CATEGORY_CODE = "general";
    private static final String DEFAULT_CATEGORY_NAME = "通用资料";
    private static final String DEFAULT_DOCUMENT_TYPE = "GENERAL_DOCUMENT";
    private static final String DEFAULT_SOURCE_TYPE = "USER_UPLOAD";

    private final DocumentFileMapper documentFileMapper;
    private final DocumentMetadataMapper documentMetadataMapper;
    private final FileUploadSessionMapper uploadSessionMapper;
    private final FileUploadChunkMapper uploadChunkMapper;
    private final DocumentProcessTaskMapper processTaskMapper;
    private final ObjectStorageService objectStorageService;
    private final FileCacheService fileCacheService;
    private final DocumentFileLookupService documentFileLookupService;
    private final FileUploadFailureService uploadFailureService;
    private final FileServiceProperties properties;
    private final ObjectMapper objectMapper;
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
    public FileUploadResponse upload(Long userId, String knowledgeBaseId, MultipartFile file, String metadataJson) {
        validateUploadFile(file, false);
        String normalizedKnowledgeBaseId = normalizeKnowledgeBaseId(knowledgeBaseId);
        String uploadId = FileIdGenerator.uploadId();
        String fileId = FileIdGenerator.fileId();
        String fileExt = FileTypeResolver.extension(file.getOriginalFilename());
        DocumentUploadMetadata metadata = normalizeUploadMetadata(parseUploadMetadata(metadataJson), file.getOriginalFilename());
        FileUploadSession session = createSession(userId, uploadId, fileId, normalizedKnowledgeBaseId, file, fileExt, 1, metadata);
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
        DocumentUploadMetadata metadata = normalizeUploadMetadata(fromMultipartRequest(request), request.getFileName());
        applyMetadataToSession(session, metadata);
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
                            session.getErrorMessage(),
                            session.getMetadataDraftJson()
                    );
                })
                .toList();
    }

    /**
     * 查询上传元信息表单选项。
     */
    @Override
    public UploadMetadataOptionsResponse uploadMetadataOptions() {
        List<UploadMetadataOptionsResponse.KnowledgeSpaceOption> spaces = List.of(
                optionSpace("personal", "个人资料库", List.of(
                        option("general", "通用资料"),
                        option("personal_note", "个人笔记"),
                        option("reading_material", "阅读资料"),
                        option("template", "模板"),
                        option("personal_other", "其他")
                )),
                optionSpace("course", "课程学习", List.of(
                        option("textbook", "教材/课本"),
                        option("lecture_note", "课件/讲义"),
                        option("course_reading", "课程阅读材料"),
                        option("homework", "作业/实验"),
                        option("lab_report", "实验报告"),
                        option("exercise", "习题/题库"),
                        option("course_project", "课程项目"),
                        option("course_other", "其他")
                )),
                optionSpace("research", "科研文献", List.of(
                        option("paper", "学术论文"),
                        option("thesis", "学位论文"),
                        option("review", "综述"),
                        option("book_chapter", "专著/章节"),
                        option("technical_report", "技术报告"),
                        option("patent_standard", "专利/标准"),
                        option("dataset_table", "数据集/表格"),
                        option("reference_bibliography", "参考文献清单"),
                        option("research_other", "其他")
                )),
                optionSpace("writing", "写作与规范", List.of(
                        option("writing_rule", "论文/报告写作要求"),
                        option("format_template", "格式模板"),
                        option("rubric", "评分标准"),
                        option("citation_rule", "引用规范"),
                        option("proposal_requirement", "开题/中期要求"),
                        option("defense_material", "答辩材料"),
                        option("writing_other", "其他")
                )),
                optionSpace("application", "申请与事务", List.of(
                        option("application_form", "申请表/报名表"),
                        option("resume", "简历"),
                        option("personal_statement", "个人陈述"),
                        option("recommendation_letter", "推荐信"),
                        option("certificate", "证书/证明"),
                        option("scholarship", "奖学金/资助"),
                        option("internship_job", "实习/就业材料"),
                        option("visa_admin", "签证/行政材料"),
                        option("application_other", "其他")
                )),
                optionSpace("project", "项目与报告", List.of(
                        option("project_report", "项目报告"),
                        option("research_plan", "研究计划"),
                        option("survey_report", "调研报告"),
                        option("meeting_minutes", "会议纪要"),
                        option("presentation", "展示/PPT"),
                        option("project_dataset", "项目数据/表格"),
                        option("project_requirement", "项目要求/说明"),
                        option("project_other", "其他")
                )),
                optionSpace("exam", "考试与复习", List.of(
                        option("exam_paper", "试卷/真题"),
                        option("review_note", "复习资料"),
                        option("mistake_note", "错题整理"),
                        option("exercise", "习题/题库"),
                        option("exam_outline", "考试大纲"),
                        option("exam_other", "其他")
                )),
                optionSpace("campus_life", "校园生活", List.of(
                        option("schedule_plan", "日程/计划"),
                        option("club_activity", "社团/活动"),
                        option("life_service", "生活服务"),
                        option("finance_receipt", "票据/报销"),
                        option("medical_health", "医疗/健康"),
                        option("campus_other", "其他")
                ))
        );
        List<UploadMetadataOptionsResponse.OptionItem> documentTypes = List.of(
                option("ACADEMIC_PAPER", "学术论文"),
                option("THESIS_DISSERTATION", "学位论文"),
                option("REVIEW_ARTICLE", "综述"),
                option("BOOK_TEXTBOOK", "教材/书籍"),
                option("COURSE_MATERIAL", "课程资料"),
                option("ASSIGNMENT_HOMEWORK", "作业/实验"),
                option("EXAM_REVIEW", "考试复习"),
                option("APPLICATION_FORM", "申请表"),
                option("RESUME_PROFILE", "简历"),
                option("CERTIFICATE_PROOF", "证书/证明"),
                option("PROJECT_REPORT", "项目报告"),
                option("RESEARCH_PROPOSAL", "研究计划/开题"),
                option("WRITING_REQUIREMENT", "写作要求"),
                option("PRESENTATION", "PPT/展示"),
                option("SPREADSHEET_TABLE", "表格/数据"),
                option("ADMINISTRATIVE_DOCUMENT", "行政事务文档"),
                option("LIFE_RECORD", "生活记录"),
                option("OTHER_DOCUMENT", "其他文档"),
                option("GENERAL_DOCUMENT", "通用文档")
        );
        List<UploadMetadataOptionsResponse.OptionItem> sourceTypes = List.of(
                option("USER_UPLOAD", "用户上传"),
                option("TEACHER_PROVIDED", "老师发放"),
                option("PAPER_DATABASE", "论文数据库"),
                option("SELF_ORGANIZED", "自己整理"),
                option("OTHER", "其他")
        );
        return new UploadMetadataOptionsResponse(spaces, documentTypes, sourceTypes);
    }

    /**
     * 查询单个文档元信息。
     */
    @Override
    public DocumentMetadataResponse getMetadata(Long userId, String fileId) {
        DocumentFile file = documentFileLookupService.requireFile(userId, fileId);
        DocumentMetadata detail = documentMetadataMapper.selectByFile(userId, fileId);
        return toMetadataResponse(file, detail);
    }

    /**
     * 保存单个文档元信息。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentMetadataResponse saveMetadata(Long userId, String fileId, DocumentMetadataRequest request) {
        DocumentFile file = documentFileLookupService.requireFile(userId, fileId);
        DocumentUploadMetadata normalized = normalizeUploadMetadata(request, file.getOriginalName());
        file.setDisplayName(normalized.getDisplayName());
        file.setKnowledgeSpaceCode(normalized.getKnowledgeSpaceCode());
        file.setKnowledgeSpaceName(normalized.getKnowledgeSpaceName());
        file.setBusinessCategoryCode(normalized.getBusinessCategoryCode());
        file.setBusinessCategoryName(normalized.getBusinessCategoryName());
        file.setDocumentType(normalized.getDocumentType());
        file.setDocumentTagsJson(toJson(defaultList(normalized.getDocumentTags())));
        file.setCourseName(normalized.getCourseName());
        file.setProjectName(normalized.getProjectName());
        file.setTermName(normalized.getTermName());
        file.setSourceType(normalized.getSourceType());
        file.setMetadataStatus("USER_FILLED");
        if (documentFileMapper.updateUserMetadata(file) == 0) {
            throw new IllegalArgumentException("文档元信息保存失败，请刷新后重试");
        }
        documentMetadataMapper.upsert(toDocumentMetadata(userId, fileId, request));
        fileCacheService.increaseVersion(userId, file.getKnowledgeBaseId());
        documentFileLookupService.cacheFileAfterCommit(file);
        return getMetadata(userId, fileId);
    }

    /**
     * 基于文件名和扩展名生成元信息建议。
     */
    @Override
    public AiMetadataSuggestResponse suggestMetadata(Long userId, String fileId) {
        DocumentFile file = documentFileLookupService.requireFile(userId, fileId);
        DocumentUploadMetadata suggestion = suggestByFileName(file.getOriginalName(), file.getFileExt());
        AiMetadataSuggestResponse response = new AiMetadataSuggestResponse();
        response.setDisplayName(suggestion.getDisplayName());
        response.setKnowledgeSpaceCode(suggestion.getKnowledgeSpaceCode());
        response.setKnowledgeSpaceName(suggestion.getKnowledgeSpaceName());
        response.setBusinessCategoryCode(suggestion.getBusinessCategoryCode());
        response.setBusinessCategoryName(suggestion.getBusinessCategoryName());
        response.setDocumentType(suggestion.getDocumentType());
        response.setDocumentTags(defaultList(suggestion.getDocumentTags()));
        response.setSourceType(suggestion.getSourceType());
        response.setReason("已根据文件名和格式生成轻量建议，真实 AI 内容解析后会继续补全文献作者、期刊和摘要等字段。");
        return response;
    }

    /**
     * 下载或预览文件。
     */
    @Override
    public ResponseEntity<InputStreamResource> download(Long userId, String fileId, boolean inline) {
        DocumentFile file = documentFileLookupService.requireFile(userId, fileId);
        InputStreamResource resource = new InputStreamResource(objectStorageService.getObject(file.getBucketName(), file.getObjectKey()));
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(downloadName(file), StandardCharsets.UTF_8)
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
                "SUCCESS".equals(parseStatus) ? request.getMetadataJson() : null,
                "SUCCESS".equals(parseStatus) ? safeInt(request.getParseQualityScore()) : 0,
                "SUCCESS".equals(parseStatus) ? safeInt(request.getParentChunkCount()) : 0,
                "SUCCESS".equals(parseStatus) ? safeInt(request.getChildChunkCount()) : 0,
                "SUCCESS".equals(parseStatus) ? safeInt(request.getAssetCount()) : 0,
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
        file.setAiMetadataJson("SUCCESS".equals(parseStatus) ? request.getMetadataJson() : null);
        file.setParseQualityScore("SUCCESS".equals(parseStatus) ? safeInt(request.getParseQualityScore()) : 0);
        file.setParentChunkCount("SUCCESS".equals(parseStatus) ? safeInt(request.getParentChunkCount()) : 0);
        file.setChildChunkCount("SUCCESS".equals(parseStatus) ? safeInt(request.getChildChunkCount()) : 0);
        file.setAssetCount("SUCCESS".equals(parseStatus) ? safeInt(request.getAssetCount()) : 0);
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
                                            int totalChunks,
                                            DocumentUploadMetadata metadata) {
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
        applyMetadataToSession(session, metadata);
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
        DocumentUploadMetadata metadata = normalizeUploadMetadata(metadataFromSession(session), session.getFileName());
        DocumentFile documentFile = new DocumentFile();
        documentFile.setFileId(session.getFileId());
        documentFile.setUserId(session.getUserId());
        documentFile.setKnowledgeBaseId(session.getKnowledgeBaseId());
        documentFile.setKnowledgeSpaceCode(metadata.getKnowledgeSpaceCode());
        documentFile.setKnowledgeSpaceName(metadata.getKnowledgeSpaceName());
        documentFile.setBusinessCategoryCode(metadata.getBusinessCategoryCode());
        documentFile.setBusinessCategoryName(metadata.getBusinessCategoryName());
        documentFile.setOriginalName(session.getFileName());
        documentFile.setDisplayName(metadata.getDisplayName());
        documentFile.setFileCategory(session.getFileCategory());
        documentFile.setFileExt(session.getFileExt());
        documentFile.setMimeType(session.getMimeType());
        documentFile.setFileSize(session.getFileSize());
        documentFile.setFileSha256(sha256);
        documentFile.setDocumentType(metadata.getDocumentType());
        documentFile.setDocumentTagsJson(toJson(defaultList(metadata.getDocumentTags())));
        documentFile.setCourseName(metadata.getCourseName());
        documentFile.setProjectName(metadata.getProjectName());
        documentFile.setTermName(metadata.getTermName());
        documentFile.setSourceType(metadata.getSourceType());
        documentFile.setStorageType("MINIO");
        documentFile.setBucketName(storedObject.getBucketName());
        documentFile.setObjectKey(storedObject.getObjectKey());
        documentFile.setUploadStatus("UPLOADED");
        documentFile.setParseStatus("NOT_REQUESTED");
        documentFile.setIndexStatus("NONE");
        documentFile.setGraphStatus("NONE");
        documentFile.setMetadataStatus(resolveMetadataStatus(session));
        documentFile.setParseQualityScore(0);
        documentFile.setParentChunkCount(0);
        documentFile.setChildChunkCount(0);
        documentFile.setAssetCount(0);
        documentFile.setParseRetryCount(0);
        documentFile.setCurrentVersion(1);
        documentFile.setEditable(Set.of("txt", "docx", "pptx").contains(session.getFileExt()) ? 1 : 0);
        documentFile.setContentHash(null);
        documentFile.setDeleted(0);
        documentFile.setCreatedAt(LocalDateTime.now());
        return documentFile;
    }

    /**
     * 把上传元信息写入上传会话。
     */
    private void applyMetadataToSession(FileUploadSession session, DocumentUploadMetadata metadata) {
        DocumentUploadMetadata normalized = normalizeUploadMetadata(metadata, session.getFileName());
        session.setDisplayName(normalized.getDisplayName());
        session.setKnowledgeSpaceCode(normalized.getKnowledgeSpaceCode());
        session.setKnowledgeSpaceName(normalized.getKnowledgeSpaceName());
        session.setBusinessCategoryCode(normalized.getBusinessCategoryCode());
        session.setBusinessCategoryName(normalized.getBusinessCategoryName());
        session.setDocumentType(normalized.getDocumentType());
        session.setDocumentTagsJson(toJson(defaultList(normalized.getDocumentTags())));
        session.setMetadataDraftJson(toJson(normalized));
    }

    /**
     * 解析普通上传表单中的元信息 JSON。
     */
    private DocumentUploadMetadata parseUploadMetadata(String metadataJson) {
        if (!StringUtils.hasText(metadataJson)) {
            return new DocumentUploadMetadata();
        }
        try {
            return objectMapper.readValue(metadataJson, DocumentUploadMetadata.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("文档元信息 JSON 格式不正确");
        }
    }

    /**
     * 从分片初始化请求中提取元信息。
     */
    private DocumentUploadMetadata fromMultipartRequest(MultipartInitRequest request) {
        DocumentUploadMetadata metadata = new DocumentUploadMetadata();
        metadata.setDisplayName(request.getDisplayName());
        metadata.setKnowledgeSpaceCode(request.getKnowledgeSpaceCode());
        metadata.setKnowledgeSpaceName(request.getKnowledgeSpaceName());
        metadata.setBusinessCategoryCode(request.getBusinessCategoryCode());
        metadata.setBusinessCategoryName(request.getBusinessCategoryName());
        metadata.setDocumentType(request.getDocumentType());
        metadata.setDocumentTags(request.getDocumentTags());
        metadata.setCourseName(request.getCourseName());
        metadata.setProjectName(request.getProjectName());
        metadata.setTermName(request.getTermName());
        metadata.setSourceType(request.getSourceType());
        if (StringUtils.hasText(request.getMetadataDraftJson())) {
            try {
                DocumentUploadMetadata draft = objectMapper.readValue(request.getMetadataDraftJson(), DocumentUploadMetadata.class);
                metadata = mergeMetadata(metadata, draft);
            } catch (Exception ignored) {
                // metadataDraftJson 是前端草稿，字段级参数仍然是权威输入；草稿解析失败时不阻塞上传。
                log.warn("分片上传元信息草稿解析失败，将使用显式字段：fileName={}", request.getFileName());
            }
        }
        return metadata;
    }

    /**
     * 从上传会话草稿恢复元信息。
     */
    private DocumentUploadMetadata metadataFromSession(FileUploadSession session) {
        if (!StringUtils.hasText(session.getMetadataDraftJson())) {
            DocumentUploadMetadata metadata = new DocumentUploadMetadata();
            metadata.setDisplayName(session.getDisplayName());
            metadata.setKnowledgeSpaceCode(session.getKnowledgeSpaceCode());
            metadata.setKnowledgeSpaceName(session.getKnowledgeSpaceName());
            metadata.setBusinessCategoryCode(session.getBusinessCategoryCode());
            metadata.setBusinessCategoryName(session.getBusinessCategoryName());
            metadata.setDocumentType(session.getDocumentType());
            metadata.setDocumentTags(readStringList(session.getDocumentTagsJson()));
            return metadata;
        }
        try {
            return objectMapper.readValue(session.getMetadataDraftJson(), DocumentUploadMetadata.class);
        } catch (Exception exception) {
            log.warn("上传会话元信息草稿解析失败，使用会话字段兜底：uploadId={}", session.getUploadId());
            DocumentUploadMetadata metadata = new DocumentUploadMetadata();
            metadata.setDisplayName(session.getDisplayName());
            metadata.setKnowledgeSpaceCode(session.getKnowledgeSpaceCode());
            metadata.setKnowledgeSpaceName(session.getKnowledgeSpaceName());
            metadata.setBusinessCategoryCode(session.getBusinessCategoryCode());
            metadata.setBusinessCategoryName(session.getBusinessCategoryName());
            metadata.setDocumentType(session.getDocumentType());
            metadata.setDocumentTags(readStringList(session.getDocumentTagsJson()));
            return metadata;
        }
    }

    /**
     * 规范化上传元信息，保证所有文件即使不填写表单也有可用默认值。
     */
    private DocumentUploadMetadata normalizeUploadMetadata(DocumentUploadMetadata metadata, String originalName) {
        DocumentUploadMetadata normalized = metadata == null ? new DocumentUploadMetadata() : metadata;
        normalized.setDisplayName(defaultIfBlank(normalized.getDisplayName(), displayNameFromOriginal(originalName)));
        normalized.setKnowledgeSpaceCode(defaultIfBlank(normalized.getKnowledgeSpaceCode(), DEFAULT_SPACE_CODE));
        normalized.setKnowledgeSpaceName(defaultIfBlank(normalized.getKnowledgeSpaceName(), resolveSpaceName(normalized.getKnowledgeSpaceCode())));
        normalized.setBusinessCategoryCode(defaultIfBlank(normalized.getBusinessCategoryCode(), DEFAULT_CATEGORY_CODE));
        normalized.setBusinessCategoryName(defaultIfBlank(normalized.getBusinessCategoryName(), resolveCategoryName(normalized.getBusinessCategoryCode())));
        normalized.setDocumentType(defaultIfBlank(normalized.getDocumentType(), DEFAULT_DOCUMENT_TYPE));
        normalized.setSourceType(defaultIfBlank(normalized.getSourceType(), DEFAULT_SOURCE_TYPE));
        normalized.setDocumentTags(defaultList(normalized.getDocumentTags()).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(12)
                .toList());
        normalized.setCourseName(trimToNull(normalized.getCourseName()));
        normalized.setProjectName(trimToNull(normalized.getProjectName()));
        normalized.setTermName(trimToNull(normalized.getTermName()));
        return normalized;
    }

    /**
     * 草稿字段和显式字段合并，显式字段优先。
     */
    private DocumentUploadMetadata mergeMetadata(DocumentUploadMetadata explicit, DocumentUploadMetadata draft) {
        DocumentUploadMetadata merged = draft == null ? new DocumentUploadMetadata() : draft;
        if (StringUtils.hasText(explicit.getDisplayName())) merged.setDisplayName(explicit.getDisplayName());
        if (StringUtils.hasText(explicit.getKnowledgeSpaceCode())) merged.setKnowledgeSpaceCode(explicit.getKnowledgeSpaceCode());
        if (StringUtils.hasText(explicit.getKnowledgeSpaceName())) merged.setKnowledgeSpaceName(explicit.getKnowledgeSpaceName());
        if (StringUtils.hasText(explicit.getBusinessCategoryCode())) merged.setBusinessCategoryCode(explicit.getBusinessCategoryCode());
        if (StringUtils.hasText(explicit.getBusinessCategoryName())) merged.setBusinessCategoryName(explicit.getBusinessCategoryName());
        if (StringUtils.hasText(explicit.getDocumentType())) merged.setDocumentType(explicit.getDocumentType());
        if (explicit.getDocumentTags() != null && !explicit.getDocumentTags().isEmpty()) merged.setDocumentTags(explicit.getDocumentTags());
        if (StringUtils.hasText(explicit.getCourseName())) merged.setCourseName(explicit.getCourseName());
        if (StringUtils.hasText(explicit.getProjectName())) merged.setProjectName(explicit.getProjectName());
        if (StringUtils.hasText(explicit.getTermName())) merged.setTermName(explicit.getTermName());
        if (StringUtils.hasText(explicit.getSourceType())) merged.setSourceType(explicit.getSourceType());
        return merged;
    }

    /**
     * 判断元信息是用户主动填写还是默认跳过。
     */
    private String resolveMetadataStatus(FileUploadSession session) {
        DocumentUploadMetadata metadata = normalizeUploadMetadata(metadataFromSession(session), session.getFileName());
        boolean changedDisplayName = !metadata.getDisplayName().equals(displayNameFromOriginal(session.getFileName()));
        boolean hasCustomTaxonomy = !DEFAULT_SPACE_CODE.equals(metadata.getKnowledgeSpaceCode())
                || !DEFAULT_CATEGORY_CODE.equals(metadata.getBusinessCategoryCode())
                || !DEFAULT_DOCUMENT_TYPE.equals(metadata.getDocumentType());
        boolean hasExtra = !defaultList(metadata.getDocumentTags()).isEmpty()
                || StringUtils.hasText(metadata.getCourseName())
                || StringUtils.hasText(metadata.getProjectName())
                || StringUtils.hasText(metadata.getTermName())
                || !DEFAULT_SOURCE_TYPE.equals(metadata.getSourceType());
        return changedDisplayName || hasCustomTaxonomy || hasExtra ? "USER_FILLED" : "USER_SKIPPED";
    }

    /**
     * 转换详细元数据保存对象。
     */
    private DocumentMetadata toDocumentMetadata(Long userId, String fileId, DocumentMetadataRequest request) {
        DocumentMetadata metadata = new DocumentMetadata();
        metadata.setFileId(fileId);
        metadata.setUserId(userId);
        metadata.setTitle(trimToNull(request.getTitle()));
        metadata.setAuthorsJson(toJson(defaultList(request.getAuthors())));
        metadata.setInstitution(trimToNull(request.getInstitution()));
        metadata.setJournal(trimToNull(request.getJournal()));
        metadata.setConferenceName(trimToNull(request.getConferenceName()));
        metadata.setPublisher(trimToNull(request.getPublisher()));
        metadata.setPublishYear(request.getPublishYear());
        metadata.setDoi(trimToNull(request.getDoi()));
        metadata.setIsbn(trimToNull(request.getIsbn()));
        metadata.setAbstractText(trimToNull(request.getAbstractText()));
        metadata.setReferenceCount(request.getReferenceCount() == null ? 0 : Math.max(0, request.getReferenceCount()));
        metadata.setAssignmentSubject(trimToNull(request.getAssignmentSubject()));
        metadata.setReportType(trimToNull(request.getReportType()));
        metadata.setRequirementType(trimToNull(request.getRequirementType()));
        metadata.setFormPurpose(trimToNull(request.getFormPurpose()));
        metadata.setExtractionSource("USER");
        metadata.setEvidenceJson("{}");
        return metadata;
    }

    /**
     * 构造元信息响应。
     */
    private DocumentMetadataResponse toMetadataResponse(DocumentFile file, DocumentMetadata detail) {
        DocumentMetadataResponse response = new DocumentMetadataResponse();
        response.setFileId(file.getFileId());
        response.setOriginalName(file.getOriginalName());
        response.setDisplayName(defaultIfBlank(file.getDisplayName(), displayNameFromOriginal(file.getOriginalName())));
        response.setKnowledgeSpaceCode(defaultIfBlank(file.getKnowledgeSpaceCode(), DEFAULT_SPACE_CODE));
        response.setKnowledgeSpaceName(defaultIfBlank(file.getKnowledgeSpaceName(), DEFAULT_SPACE_NAME));
        response.setBusinessCategoryCode(defaultIfBlank(file.getBusinessCategoryCode(), DEFAULT_CATEGORY_CODE));
        response.setBusinessCategoryName(defaultIfBlank(file.getBusinessCategoryName(), DEFAULT_CATEGORY_NAME));
        response.setDocumentType(defaultIfBlank(file.getDocumentType(), DEFAULT_DOCUMENT_TYPE));
        response.setDocumentTags(readStringList(file.getDocumentTagsJson()));
        response.setCourseName(file.getCourseName());
        response.setProjectName(file.getProjectName());
        response.setTermName(file.getTermName());
        response.setSourceType(defaultIfBlank(file.getSourceType(), DEFAULT_SOURCE_TYPE));
        response.setMetadataStatus(defaultIfBlank(file.getMetadataStatus(), "USER_SKIPPED"));
        response.setAiMetadataJson(file.getAiMetadataJson());
        if (detail != null) {
            response.setTitle(detail.getTitle());
            response.setAuthors(readStringList(detail.getAuthorsJson()));
            response.setInstitution(detail.getInstitution());
            response.setJournal(detail.getJournal());
            response.setConferenceName(detail.getConferenceName());
            response.setPublisher(detail.getPublisher());
            response.setPublishYear(detail.getPublishYear());
            response.setDoi(detail.getDoi());
            response.setIsbn(detail.getIsbn());
            response.setAbstractText(detail.getAbstractText());
            response.setReferenceCount(detail.getReferenceCount());
            response.setAssignmentSubject(detail.getAssignmentSubject());
            response.setReportType(detail.getReportType());
            response.setRequirementType(detail.getRequirementType());
            response.setFormPurpose(detail.getFormPurpose());
        }
        return response;
    }

    /**
     * 基于文件名做轻量元信息建议。
     */
    private DocumentUploadMetadata suggestByFileName(String fileName, String fileExt) {
        String normalizedName = fileName == null ? "" : fileName.toLowerCase();
        DocumentUploadMetadata metadata = new DocumentUploadMetadata();
        metadata.setDisplayName(displayNameFromOriginal(fileName));
        metadata.setSourceType(DEFAULT_SOURCE_TYPE);
        if (normalizedName.contains("论文") || normalizedName.contains("paper") || normalizedName.contains("doi")) {
            metadata.setKnowledgeSpaceCode("research");
            metadata.setBusinessCategoryCode("paper");
            metadata.setDocumentType("ACADEMIC_PAPER");
            metadata.setDocumentTags(List.of("论文"));
        } else if (normalizedName.contains("作业") || normalizedName.contains("实验")) {
            metadata.setKnowledgeSpaceCode("course");
            metadata.setBusinessCategoryCode("homework");
            metadata.setDocumentType("ASSIGNMENT_HOMEWORK");
            metadata.setDocumentTags(List.of("作业"));
        } else if (normalizedName.contains("申请") || normalizedName.contains("报名")) {
            metadata.setKnowledgeSpaceCode("application");
            metadata.setBusinessCategoryCode("application_form");
            metadata.setDocumentType("APPLICATION_FORM");
            metadata.setDocumentTags(List.of("申请"));
        } else if (normalizedName.contains("简历") || normalizedName.contains("resume")) {
            metadata.setKnowledgeSpaceCode("application");
            metadata.setBusinessCategoryCode("resume");
            metadata.setDocumentType("RESUME_PROFILE");
            metadata.setDocumentTags(List.of("简历"));
        } else if (normalizedName.contains("要求") || normalizedName.contains("规范")) {
            metadata.setKnowledgeSpaceCode("writing");
            metadata.setBusinessCategoryCode("writing_rule");
            metadata.setDocumentType("WRITING_REQUIREMENT");
            metadata.setDocumentTags(List.of("写作要求"));
        } else if ("ppt".equals(fileExt) || "pptx".equals(fileExt)) {
            metadata.setKnowledgeSpaceCode("project");
            metadata.setBusinessCategoryCode("presentation");
            metadata.setDocumentType("PRESENTATION");
            metadata.setDocumentTags(List.of("展示"));
        } else {
            metadata.setKnowledgeSpaceCode(DEFAULT_SPACE_CODE);
            metadata.setBusinessCategoryCode(DEFAULT_CATEGORY_CODE);
            metadata.setDocumentType(DEFAULT_DOCUMENT_TYPE);
            metadata.setDocumentTags(List.of());
        }
        metadata.setKnowledgeSpaceName(resolveSpaceName(metadata.getKnowledgeSpaceCode()));
        metadata.setBusinessCategoryName(resolveCategoryName(metadata.getBusinessCategoryCode()));
        return normalizeUploadMetadata(metadata, fileName);
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
        item.setName(defaultIfBlank(session.getDisplayName(), displayNameFromOriginal(session.getFileName())));
        item.setOriginalName(session.getFileName());
        item.setDisplayName(defaultIfBlank(session.getDisplayName(), displayNameFromOriginal(session.getFileName())));
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
        item.setKnowledgeSpaceCode(session.getKnowledgeSpaceCode());
        item.setKnowledgeSpaceName(session.getKnowledgeSpaceName());
        item.setBusinessCategoryCode(session.getBusinessCategoryCode());
        item.setBusinessCategoryName(session.getBusinessCategoryName());
        item.setDocumentType(session.getDocumentType());
        item.setDocumentTagsJson(session.getDocumentTagsJson());
        item.setMetadataStatus(resolveMetadataStatus(session));
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
     * 空字符串转 null，避免把无意义空白写入数据库。
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 获取非空列表。
     */
    private List<String> defaultList(List<String> values) {
        return values == null ? List.of() : values;
    }

    /**
     * 安全读取整数，空值按 0 处理。
     */
    private Integer safeInt(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    /**
     * 对象序列化为 JSON 字符串。
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("文档元信息序列化失败", exception);
        }
    }

    /**
     * 从 JSON 数组读取字符串列表。
     */
    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }

    /**
     * 根据原始文件名生成默认展示名。
     */
    private String displayNameFromOriginal(String originalName) {
        if (!StringUtils.hasText(originalName)) {
            return "未命名文档";
        }
        String trimmed = originalName.trim();
        int index = trimmed.lastIndexOf('.');
        return index > 0 ? trimmed.substring(0, index) : trimmed;
    }

    /**
     * 构造下载文件名，展示名没有扩展名时自动补回原扩展名。
     */
    private String downloadName(DocumentFile file) {
        String displayName = defaultIfBlank(file.getDisplayName(), displayNameFromOriginal(file.getOriginalName()));
        String ext = FileTypeResolver.extension(file.getOriginalName());
        if (!StringUtils.hasText(ext) || displayName.toLowerCase().endsWith("." + ext)) {
            return displayName;
        }
        return displayName + "." + ext;
    }

    /**
     * 构造知识域选项。
     */
    private UploadMetadataOptionsResponse.KnowledgeSpaceOption optionSpace(
            String code,
            String name,
            List<UploadMetadataOptionsResponse.OptionItem> categories
    ) {
        return new UploadMetadataOptionsResponse.KnowledgeSpaceOption(code, name, categories);
    }

    /**
     * 构造通用选项。
     */
    private UploadMetadataOptionsResponse.OptionItem option(String code, String name) {
        return new UploadMetadataOptionsResponse.OptionItem(code, name);
    }

    /**
     * 根据知识域编码解析展示名。
     */
    private String resolveSpaceName(String code) {
        return switch (defaultIfBlank(code, DEFAULT_SPACE_CODE)) {
            case "course" -> "课程学习";
            case "research" -> "科研文献";
            case "writing" -> "写作与规范";
            case "application" -> "申请与事务";
            case "project" -> "项目与报告";
            case "exam" -> "考试与复习";
            case "campus_life" -> "校园生活";
            default -> DEFAULT_SPACE_NAME;
        };
    }

    /**
     * 根据二级分类编码解析展示名。
     */
    private String resolveCategoryName(String code) {
        return switch (defaultIfBlank(code, DEFAULT_CATEGORY_CODE)) {
            case "paper" -> "学术论文";
            case "thesis" -> "学位论文";
            case "review" -> "综述";
            case "book_chapter" -> "专著/章节";
            case "technical_report" -> "技术报告";
            case "patent_standard" -> "专利/标准";
            case "lecture_note" -> "课件/讲义";
            case "textbook" -> "教材/课本";
            case "course_reading" -> "课程阅读材料";
            case "homework" -> "作业/实验";
            case "lab_report" -> "实验报告";
            case "exercise" -> "习题/题库";
            case "course_project" -> "课程项目";
            case "report" -> "报告/调研";
            case "project_report" -> "项目报告";
            case "research_plan" -> "研究计划";
            case "survey_report" -> "调研报告";
            case "meeting_minutes" -> "会议纪要";
            case "writing_rule" -> "论文/报告写作要求";
            case "format_template" -> "格式模板";
            case "rubric" -> "评分标准";
            case "citation_rule" -> "引用规范";
            case "proposal_requirement" -> "开题/中期要求";
            case "defense_material" -> "答辩材料";
            case "application_form" -> "申请表/报名表";
            case "resume" -> "简历";
            case "personal_statement" -> "个人陈述";
            case "recommendation_letter" -> "推荐信";
            case "certificate" -> "证书/证明";
            case "scholarship" -> "奖学金/资助";
            case "internship_job" -> "实习/就业材料";
            case "visa_admin" -> "签证/行政材料";
            case "template" -> "模板";
            case "personal_note" -> "个人笔记";
            case "reading_material" -> "阅读资料";
            case "dataset_table" -> "表格/数据";
            case "reference_bibliography" -> "参考文献清单";
            case "presentation" -> "展示/PPT";
            case "project_dataset" -> "项目数据/表格";
            case "project_requirement" -> "项目要求/说明";
            case "exam" -> "考试复习";
            case "exam_paper" -> "试卷/真题";
            case "review_note" -> "复习资料";
            case "mistake_note" -> "错题整理";
            case "exam_outline" -> "考试大纲";
            case "schedule_plan" -> "日程/计划";
            case "club_activity" -> "社团/活动";
            case "life_service" -> "生活服务";
            case "finance_receipt" -> "票据/报销";
            case "medical_health" -> "医疗/健康";
            case "personal_other", "course_other", "research_other", "writing_other",
                    "application_other", "project_other", "exam_other", "campus_other" -> "其他";
            default -> DEFAULT_CATEGORY_NAME;
        };
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
