package com.xyf.docnexus.file.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyf.docnexus.common.constant.MqTopicConstants;
import com.xyf.docnexus.file.config.FileServiceProperties;
import com.xyf.docnexus.file.dto.DocumentReparseEvent;
import com.xyf.docnexus.file.dto.OnlyOfficeCallbackRequest;
import com.xyf.docnexus.file.dto.OnlyOfficeConfigResponse;
import com.xyf.docnexus.file.dto.OnlyOfficeForceSaveRequest;
import com.xyf.docnexus.file.dto.OnlyOfficeForceSaveResponse;
import com.xyf.docnexus.file.dto.StoredObject;
import com.xyf.docnexus.file.entity.DocumentContentRevisionLog;
import com.xyf.docnexus.file.entity.DocumentFile;
import com.xyf.docnexus.file.entity.DocumentProcessTask;
import com.xyf.docnexus.file.mapper.DocumentContentRevisionLogMapper;
import com.xyf.docnexus.file.mapper.DocumentFileMapper;
import com.xyf.docnexus.file.mapper.DocumentProcessTaskMapper;
import com.xyf.docnexus.file.log.FileBusinessLogPublisher;
import com.xyf.docnexus.file.service.DocumentFileLookupService;
import com.xyf.docnexus.file.service.FileCacheService;
import com.xyf.docnexus.file.service.ObjectStorageService;
import com.xyf.docnexus.file.service.OnlyOfficeService;
import com.xyf.docnexus.file.util.FileIdGenerator;
import com.xyf.docnexus.file.util.FileRedisKeys;
import com.xyf.docnexus.file.util.OnlyOfficeJwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * OnlyOffice 在线编辑服务实现。
 */
@Slf4j
@Service
public class OnlyOfficeServiceImpl implements OnlyOfficeService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("docx", "pptx", "txt");
    private static final int DOWNLOAD_BUFFER_SIZE = 8192;

    private final DocumentFileMapper documentFileMapper;
    private final DocumentContentRevisionLogMapper revisionLogMapper;
    private final DocumentProcessTaskMapper processTaskMapper;
    private final ObjectStorageService objectStorageService;
    private final FileCacheService fileCacheService;
    private final DocumentFileLookupService documentFileLookupService;
    private final FileBusinessLogPublisher businessLogPublisher;
    private final FileServiceProperties properties;
    private final OnlyOfficeJwtUtils jwtUtils;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RocketMQTemplate rocketMQTemplate;

    public OnlyOfficeServiceImpl(DocumentFileMapper documentFileMapper,
                                 DocumentContentRevisionLogMapper revisionLogMapper,
                                 DocumentProcessTaskMapper processTaskMapper,
                                 ObjectStorageService objectStorageService,
                                 FileCacheService fileCacheService,
                                 DocumentFileLookupService documentFileLookupService,
                                 FileBusinessLogPublisher businessLogPublisher,
                                 FileServiceProperties properties,
                                 OnlyOfficeJwtUtils jwtUtils,
                                 StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper,
                                 ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider) {
        this.documentFileMapper = documentFileMapper;
        this.revisionLogMapper = revisionLogMapper;
        this.processTaskMapper = processTaskMapper;
        this.objectStorageService = objectStorageService;
        this.fileCacheService = fileCacheService;
        this.documentFileLookupService = documentFileLookupService;
        this.businessLogPublisher = businessLogPublisher;
        this.properties = properties;
        this.jwtUtils = jwtUtils;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.rocketMQTemplate = rocketMQTemplateProvider.getIfAvailable();
    }

    /**
     * 生成 OnlyOffice 编辑器配置。
     */
    @Override
    public OnlyOfficeConfigResponse buildConfig(Long userId, String username, String fileId) {
        DocumentFile file = documentFileLookupService.requireFile(userId, fileId);
        String fileExt = normalizedExt(file);
        if (!properties.getOnlyoffice().isEnabled()) {
            throw new IllegalStateException("OnlyOffice 在线编辑未启用");
        }
        if (!SUPPORTED_EXTENSIONS.contains(fileExt)) {
            throw new IllegalArgumentException("当前文件格式暂不支持 OnlyOffice 在线编辑");
        }

        int currentVersion = normalizedVersion(file);
        String documentKey = documentKey(file, currentVersion);
        String sourceToken = signAccessToken(file, currentVersion, documentKey, "SOURCE", properties.getOnlyoffice().getSourceTokenTtlSeconds());
        String callbackToken = signAccessToken(file, currentVersion, documentKey, "CALLBACK", properties.getOnlyoffice().getCallbackTokenTtlSeconds());
        String sourceUrl = buildCallbackBaseUrl() + "/api/files/" + fileId + "/onlyoffice/source?token=" + encode(sourceToken);
        String callbackUrl = buildCallbackBaseUrl() + "/api/files/" + fileId + "/onlyoffice/callback?token=" + encode(callbackToken);

        Map<String, Object> config = buildEditorConfig(file, userId, username, currentVersion, documentKey, sourceUrl, callbackUrl);
        String configToken = jwtUtils.sign(config, properties.getOnlyoffice().getJwtSecret(), properties.getOnlyoffice().getConfigTokenTtlSeconds());
        config.put("token", configToken);
        String documentServerApiUrl = trimTrailingSlash(properties.getOnlyoffice().getPublicUrl()) + "/web-apps/apps/api/documents/api.js";
        Map<String, Object> diagnostics = diagnoseDocumentServer(documentServerApiUrl);
        log.info("OnlyOffice 生成编辑配置，fileId={}, userId={}, ext={}, version={}, documentKey={}, publicUrl={}, internalUrl={}, callbackBaseUrl={}, apiUrl={}, sourcePath={}, callbackPath={}",
                file.getFileId(),
                userId,
                fileExt,
                currentVersion,
                documentKey,
                trimTrailingSlash(properties.getOnlyoffice().getPublicUrl()),
                trimTrailingSlash(properties.getOnlyoffice().getInternalUrl()),
                buildCallbackBaseUrl(),
                documentServerApiUrl,
                safePath(sourceUrl),
                safePath(callbackUrl));

        return new OnlyOfficeConfigResponse(
                file.getFileId(),
                displayName(file),
                file.getFileExt(),
                true,
                currentVersion,
                documentKey,
                documentServerApiUrl,
                config,
                diagnostics,
                file.getParseStatus(),
                file.getIndexStatus(),
                file.getUpdatedAt()
        );
    }

    /**
     * 给 OnlyOffice 返回当前 MinIO 文件流。
     */
    @Override
    public ResponseEntity<InputStreamResource> source(String fileId, String token) {
        Map<String, Object> claims = verifyAccessToken(token, "SOURCE", fileId);
        Long userId = claimLong(claims, "userId");
        Integer version = claimInteger(claims, "version");
        DocumentFile file = documentFileLookupService.requireFile(userId, fileId);
        if (!Integer.valueOf(normalizedVersion(file)).equals(version)) {
            throw new IllegalArgumentException("OnlyOffice 源文件 token 已过期，请重新打开文档");
        }

        log.info("OnlyOffice 源文件验签通过，fileId={}, userId={}, version={}, bucket={}, objectKey={}, fileSize={}",
                fileId, userId, version, file.getBucketName(), file.getObjectKey(), file.getFileSize());
        InputStream inputStream = objectStorageService.getObject(file.getBucketName(), file.getObjectKey());
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(file.getOriginalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType(normalizedExt(file))))
                .contentLength(file.getFileSize() == null ? -1 : file.getFileSize())
                .header("Content-Disposition", disposition.toString())
                .body(new InputStreamResource(inputStream));
    }

    /**
     * 触发 OnlyOffice 手动强制保存并等待保存回调完成。
     */
    @Override
    public OnlyOfficeForceSaveResponse forceSave(Long userId, String fileId, OnlyOfficeForceSaveRequest request) {
        DocumentFile file = documentFileLookupService.requireFile(userId, fileId);
        String fileExt = normalizedExt(file);
        if (!properties.getOnlyoffice().isEnabled()) {
            throw new IllegalStateException("OnlyOffice 在线编辑未启用");
        }
        if (!SUPPORTED_EXTENSIONS.contains(fileExt)) {
            throw new IllegalArgumentException("当前文件格式暂不支持 OnlyOffice 在线编辑");
        }

        int currentVersion = normalizedVersion(file);
        if (request == null || request.getCurrentVersion() == null) {
            throw new IllegalArgumentException("手动保存缺少当前文档版本");
        }
        if (!Integer.valueOf(currentVersion).equals(request.getCurrentVersion())) {
            throw new IllegalArgumentException("文档版本已变化，请重新打开后保存");
        }

        String documentKey = documentKey(file, currentVersion);
        if (StringUtils.hasText(request.getDocumentKey()) && !documentKey.equals(request.getDocumentKey())) {
            throw new IllegalArgumentException("文档编辑会话已过期，请重新打开后保存");
        }

        String requestId = FileIdGenerator.compactUuid();
        redisTemplate.delete(FileRedisKeys.onlyOfficeForceSaveResultKey(requestId));
        Map<String, Object> commandResponse = sendForceSaveCommand(documentKey, requestId);
        int commandError = commandError(commandResponse);
        if (commandError == 4) {
            return new OnlyOfficeForceSaveResponse(false, currentVersion, file.getContentHash(), "用户未更改，无需保存");
        }
        if (commandError != 0) {
            throw new IllegalStateException("OnlyOffice 手动保存命令失败，error=" + commandError);
        }

        OnlyOfficeForceSaveResponse waitResult = waitForceSaveResult(requestId, currentVersion, file.getContentHash());
        log.info("OnlyOffice 手动保存完成，fileId={}, userId={}, requestId={}, saved={}, version={}",
                fileId, userId, requestId, waitResult.getSaved(), waitResult.getCurrentVersion());
        return waitResult;
    }

    /**
     * 处理 OnlyOffice 保存回调。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Integer> callback(String fileId, String token, OnlyOfficeCallbackRequest request) {
        long startNano = System.nanoTime();
        LocalDateTime occurredAt = LocalDateTime.now();
        boolean shouldLogSave = request != null
                && request.getStatus() != null
                && (request.getStatus() == 2 || request.getStatus() == 6);
        Long operationUserId = null;
        try {
            Map<String, Object> claims = verifyAccessToken(token, "CALLBACK", fileId);
            if (request == null || request.getStatus() == null) {
                return callbackOk();
            }
            if (request.getStatus() != 2 && request.getStatus() != 6) {
                return callbackOk();
            }
            if (!StringUtils.hasText(request.getUrl())) {
                log.warn("OnlyOffice 保存回调缺少下载地址，fileId={}, status={}", fileId, request.getStatus());
                publishForceSaveResult(request.getUserdata(), false, null, null, "保存失败：OnlyOffice 回调缺少下载地址");
                return callbackError();
            }

            String expectedKey = claimString(claims, "documentKey");
            if (StringUtils.hasText(request.getKey()) && !expectedKey.equals(request.getKey())) {
                throw new IllegalArgumentException("OnlyOffice 回调 documentKey 不匹配");
            }
            Long userId = claimLong(claims, "userId");
            operationUserId = userId;
            Integer currentVersion = claimInteger(claims, "version");
            SavedOfficeFileResult saveResult = saveEditedFile(userId, fileId, currentVersion, request.getUrl());
            registerForceSaveResultAfterCommit(request.getUserdata(), true, saveResult.currentVersion(), saveResult.contentHash(), "已保存成功");
            businessLogPublisher.publishOnlyOfficeSaveSuccessAfterCommit(userId, fileId, occurredAt, startNano);
            return callbackOk();
        } catch (Exception exception) {
            // OnlyOffice 回调必须返回 error JSON，捕获异常后要显式回滚当前事务，避免半截保存结果提交。
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            if (request != null) {
                publishForceSaveResult(request.getUserdata(), false, null, null, "保存失败：" + exception.getMessage());
            }
            if (shouldLogSave) {
                businessLogPublisher.publishOnlyOfficeSaveFailure(operationUserId, fileId, occurredAt, startNano, exception.getMessage());
            }
            log.warn("OnlyOffice 保存回调处理失败，fileId={}", fileId, exception);
            return callbackError();
        }
    }

    /**
     * 保存 OnlyOffice 生成的新文件。
     */
    private SavedOfficeFileResult saveEditedFile(Long userId, String fileId, Integer currentVersion, String editedFileUrl) throws Exception {
        String lockKey = FileRedisKeys.editorSaveLockKey(fileId);
        String lockToken = fileCacheService.tryLock(lockKey, Duration.ofMinutes(5));
        if (lockToken == null) {
            throw new IllegalArgumentException("文档正在保存，请稍后再试");
        }

        DownloadedOfficeFile downloaded = null;
        try {
            DocumentFile file = documentFileLookupService.requireFile(userId, fileId);
            if (!Integer.valueOf(normalizedVersion(file)).equals(currentVersion)) {
                throw new IllegalArgumentException("文档版本已变化，请重新打开后保存");
            }

            int newVersion = currentVersion + 1;
            downloaded = downloadEditedFile(editedFileUrl, normalizedExt(file));
            StoredObject storedObject = new StoredObject(file.getBucketName(), file.getObjectKey());

            int updatedRows = documentFileMapper.updateEditorObjectByVersion(
                    userId,
                    fileId,
                    currentVersion,
                    file.getBucketName(),
                    file.getObjectKey(),
                    downloaded.size(),
                    downloaded.sha256(),
                    downloaded.sha256()
            );
            if (updatedRows == 0) {
                throw new IllegalArgumentException("文档已被更新，请重新打开后保存");
            }

            String eventId = FileIdGenerator.compactUuid();
            saveRevisionLog(file, storedObject, currentVersion, newVersion, downloaded.sha256(), eventId);
            createParseTask(userId, fileId);
            try (InputStream stream = Files.newInputStream(downloaded.path())) {
                objectStorageService.overwriteObject(
                        file.getBucketName(),
                        file.getObjectKey(),
                        stream,
                        downloaded.size(),
                        contentType(normalizedExt(file))
                );
            }
            fileCacheService.increaseVersion(userId, file.getKnowledgeBaseId());
            sendReparseEvent(file, storedObject, newVersion, eventId);
            refreshSavedFileCache(file, downloaded, newVersion);
            return new SavedOfficeFileResult(newVersion, downloaded.sha256());
        } finally {
            if (downloaded != null) {
                Files.deleteIfExists(downloaded.path());
            }
            fileCacheService.unlock(lockKey, lockToken);
        }
    }

    /**
     * 构造 OnlyOffice 编辑器配置主体。
     */
    private Map<String, Object> buildEditorConfig(DocumentFile file,
                                                  Long userId,
                                                  String username,
                                                  int currentVersion,
                                                  String documentKey,
                                                  String sourceUrl,
                                                  String callbackUrl) {
        String fileExt = normalizedExt(file);
        Map<String, Object> permissions = new LinkedHashMap<>();
        permissions.put("edit", true);
        permissions.put("download", false);
        permissions.put("print", false);
        permissions.put("comment", false);

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("title", displayName(file));
        document.put("url", sourceUrl);
        document.put("fileType", fileExt);
        document.put("key", documentKey);
        document.put("permissions", permissions);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", String.valueOf(userId));
        user.put("name", StringUtils.hasText(username) ? username : "DocNexus 用户");

        Map<String, Object> customization = new LinkedHashMap<>();
        customization.put("autosave", true);
        customization.put("forcesave", true);
        customization.put("comments", false);
        customization.put("chat", false);
        customization.put("help", false);
        customization.put("compactHeader", false);

        Map<String, Object> editorConfig = new LinkedHashMap<>();
        editorConfig.put("mode", "edit");
        editorConfig.put("lang", "zh-CN");
        editorConfig.put("callbackUrl", callbackUrl);
        editorConfig.put("user", user);
        editorConfig.put("customization", customization);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("type", "desktop");
        config.put("documentType", documentType(fileExt));
        config.put("document", document);
        config.put("editorConfig", editorConfig);
        config.put("height", "100%");
        config.put("width", "100%");
        config.put("docnexusVersion", currentVersion);
        return config;
    }

    /**
     * 诊断 Document Server 是否完整暴露。
     *
     * <p>很多“OnlyOffice 一直加载中”的问题不是 api.js 访问失败，而是反向代理只暴露了
     * /web-apps 静态资源，未暴露 /coauthoring 等后端接口。这里用短超时探测，返回给前端展示，
     * 不阻断正常编辑流程。</p>
     */
    private Map<String, Object> diagnoseDocumentServer(String documentServerApiUrl) {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("apiReachable", false);
        diagnostics.put("backendExposed", false);
        diagnostics.put("message", "");
        try {
            String baseUrl = trimTrailingSlash(properties.getOnlyoffice().getPublicUrl());
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            HttpResponse<String> apiResponse = client.send(
                    HttpRequest.newBuilder(URI.create(documentServerApiUrl))
                            .timeout(Duration.ofSeconds(5))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            boolean apiReachable = apiResponse.statusCode() >= 200
                    && apiResponse.statusCode() < 300
                    && apiResponse.body() != null
                    && apiResponse.body().contains("DocsAPI");
            diagnostics.put("apiReachable", apiReachable);
            diagnostics.put("apiStatus", apiResponse.statusCode());

            HttpResponse<String> commandResponse = client.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/coauthoring/CommandService.ashx"))
                            .timeout(Duration.ofSeconds(5))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"c\":\"version\"}", StandardCharsets.UTF_8))
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            boolean backendExposed = commandResponse.statusCode() != 404;
            diagnostics.put("backendExposed", backendExposed);
            diagnostics.put("commandStatus", commandResponse.statusCode());
            if (!apiReachable) {
                diagnostics.put("message", "OnlyOffice API 脚本不可访问，请检查 ONLYOFFICE_PUBLIC_URL。");
            } else if (!backendExposed) {
                diagnostics.put("message", "OnlyOffice 只暴露了 web-apps 静态资源，Document Server 后端接口未暴露，请修复服务器反向代理或端口映射。");
            }
        } catch (Exception exception) {
            diagnostics.put("message", "OnlyOffice 连通性诊断失败：" + exception.getMessage());
        }
        return diagnostics;
    }

    /**
     * 下载 OnlyOffice 回调给出的编辑后文件，并计算 SHA-256。
     */
    private DownloadedOfficeFile downloadEditedFile(String editedFileUrl, String fileExt) throws Exception {
        URI uri = URI.create(editedFileUrl);
        if (!isAllowedDocumentServerUrl(uri)) {
            throw new IllegalArgumentException("OnlyOffice 回调下载地址不在允许范围内");
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getOnlyoffice().getCallbackDownloadTimeoutSeconds())))
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(Math.max(1, properties.getOnlyoffice().getCallbackDownloadTimeoutSeconds())))
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("OnlyOffice 编辑文件下载失败，HTTP " + response.statusCode());
        }

        long maxSize = properties.getUpload().getMaxSizeBytes();
        Path tempFile = Files.createTempFile("docnexus-onlyoffice-", "." + fileExt);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long total = 0;
        try (InputStream inputStream = response.body();
             var outputStream = Files.newOutputStream(tempFile)) {
            byte[] buffer = new byte[DOWNLOAD_BUFFER_SIZE];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (total > maxSize) {
                    throw new IllegalArgumentException("OnlyOffice 编辑文件超过 200MB");
                }
                digest.update(buffer, 0, read);
                outputStream.write(buffer, 0, read);
            }
        } catch (Exception exception) {
            Files.deleteIfExists(tempFile);
            throw exception;
        }
        return new DownloadedOfficeFile(tempFile, total, toHex(digest.digest()));
    }

    /**
     * 调用 OnlyOffice Command Service 触发 forcesave。
     */
    private Map<String, Object> sendForceSaveCommand(String documentKey, String requestId) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("c", "forcesave");
            payload.put("key", documentKey);
            payload.put("userdata", requestId);
            String token = jwtUtils.sign(payload, properties.getOnlyoffice().getJwtSecret(), 120);

            Map<String, Object> body = new LinkedHashMap<>(payload);
            body.put("token", token);
            String requestBody = objectMapper.writeValueAsString(body);

            HttpResponse<String> response = sendOnlyOfficeCommandRequest(commandUrl("/command", documentKey), requestBody, token);
            if (response.statusCode() == 404) {
                response = sendOnlyOfficeCommandRequest(commandUrl("/coauthoring/CommandService.ashx", documentKey), requestBody, token);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OnlyOffice 命令服务返回 HTTP " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("调用 OnlyOffice 手动保存命令失败：" + exception.getMessage(), exception);
        }
    }

    /**
     * 发送 OnlyOffice 命令服务 HTTP 请求。
     */
    private HttpResponse<String> sendOnlyOfficeCommandRequest(String url, String requestBody, String token) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * 等待 OnlyOffice callback 写入手动保存结果。
     */
    private OnlyOfficeForceSaveResponse waitForceSaveResult(String requestId, Integer currentVersion, String contentHash) {
        String resultKey = FileRedisKeys.onlyOfficeForceSaveResultKey(requestId);
        long waitMillis = Math.max(5, properties.getOnlyoffice().getForceSaveWaitSeconds()) * 1000L;
        long deadline = System.currentTimeMillis() + waitMillis;
        while (System.currentTimeMillis() < deadline) {
            String value = redisTemplate.opsForValue().get(resultKey);
            if (StringUtils.hasText(value)) {
                redisTemplate.delete(resultKey);
                OnlyOfficeForceSaveResponse response = parseForceSaveResult(value);
                if (Boolean.TRUE.equals(response.getSaved())) {
                    return response;
                }
                throw new IllegalStateException(response.getMessage());
            }
            sleepForceSavePollInterval();
        }
        throw new IllegalStateException("保存请求已发送，但暂未收到 OnlyOffice 保存回调，请稍后重试");
    }

    /**
     * 解析 callback 写入的保存结果。
     */
    private OnlyOfficeForceSaveResponse parseForceSaveResult(String value) {
        try {
            return objectMapper.readValue(value, OnlyOfficeForceSaveResponse.class);
        } catch (Exception exception) {
            throw new IllegalStateException("解析 OnlyOffice 保存结果失败", exception);
        }
    }

    /**
     * 保存轮询间隔，避免手动保存接口高频打 Redis。
     */
    private void sleepForceSavePollInterval() {
        try {
            Thread.sleep(300L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 OnlyOffice 保存结果被中断", exception);
        }
    }

    /**
     * 事务提交后发布手动保存结果，确保前端看到成功时数据库和 MinIO 已经完成覆盖。
     */
    private void registerForceSaveResultAfterCommit(String requestId,
                                                    boolean saved,
                                                    Integer currentVersion,
                                                    String contentHash,
                                                    String message) {
        if (!StringUtils.hasText(requestId)) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishForceSaveResult(requestId, saved, currentVersion, contentHash, message);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * 事务提交后写入 Redis，避免数据库回滚时前端误以为已保存成功。
             */
            @Override
            public void afterCommit() {
                publishForceSaveResult(requestId, saved, currentVersion, contentHash, message);
            }
        });
    }

    /**
     * 写入手动保存回调结果。
     */
    private void publishForceSaveResult(String requestId,
                                        boolean saved,
                                        Integer currentVersion,
                                        String contentHash,
                                        String message) {
        if (!StringUtils.hasText(requestId)) {
            return;
        }
        try {
            OnlyOfficeForceSaveResponse response = new OnlyOfficeForceSaveResponse(saved, currentVersion, contentHash, message);
            redisTemplate.opsForValue().set(
                    FileRedisKeys.onlyOfficeForceSaveResultKey(requestId),
                    objectMapper.writeValueAsString(response),
                    Duration.ofMinutes(2)
            );
        } catch (Exception exception) {
            log.warn("写入 OnlyOffice 手动保存结果失败，requestId={}", requestId, exception);
        }
    }

    /**
     * 读取 OnlyOffice 命令服务错误码。
     */
    private int commandError(Map<String, Object> commandResponse) {
        Object error = commandResponse == null ? null : commandResponse.get("error");
        if (error instanceof Number number) {
            return number.intValue();
        }
        if (error == null) {
            return 0;
        }
        return Integer.parseInt(String.valueOf(error));
    }

    /**
     * 签发源文件或回调访问 token。
     */
    private String signAccessToken(DocumentFile file, int currentVersion, String documentKey, String purpose, long ttlSeconds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("purpose", purpose);
        payload.put("fileId", file.getFileId());
        payload.put("userId", file.getUserId());
        payload.put("version", currentVersion);
        payload.put("documentKey", documentKey);
        payload.put("fileSha256", file.getFileSha256());
        return jwtUtils.sign(payload, properties.getOnlyoffice().getJwtSecret(), ttlSeconds);
    }

    /**
     * 验证源文件或回调访问 token。
     */
    private Map<String, Object> verifyAccessToken(String token, String purpose, String fileId) {
        Map<String, Object> claims = jwtUtils.verify(token, properties.getOnlyoffice().getJwtSecret());
        if (!purpose.equals(claimString(claims, "purpose"))) {
            throw new IllegalArgumentException("OnlyOffice token 用途不匹配");
        }
        if (!fileId.equals(claimString(claims, "fileId"))) {
            throw new IllegalArgumentException("OnlyOffice token 文件不匹配");
        }
        return claims;
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
     * OnlyOffice 保存成功后刷新单文件元数据缓存。
     */
    private void refreshSavedFileCache(DocumentFile file, DownloadedOfficeFile downloaded, int newVersion) {
        file.setFileSize(downloaded.size());
        file.setFileSha256(downloaded.sha256());
        file.setContentHash(downloaded.sha256());
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
     * 创建等待重新解析任务。
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
     * 投递重新解析 MQ 事件。
     */
    private void sendReparseEvent(DocumentFile file, StoredObject storedObject, int newVersion, String eventId) {
        if (rocketMQTemplate == null) {
            log.warn("RocketMQTemplate 不可用，跳过 OnlyOffice 重新解析事件发送，fileId={}, eventId={}", file.getFileId(), eventId);
            return;
        }
        DocumentReparseEvent event = new DocumentReparseEvent(
                eventId,
                file.getFileId(),
                file.getUserId(),
                newVersion,
                storedObject.getBucketName(),
                storedObject.getObjectKey(),
                "ONLYOFFICE_SAVE",
                LocalDateTime.now()
        );
        String destination = MqTopicConstants.FILE_EVENT_TOPIC + ":" + MqTopicConstants.TAG_DOCUMENT_REPARSE_REQUESTED;
        try {
            rocketMQTemplate.convertAndSend(destination, event);
        } catch (Exception exception) {
            log.warn("发送 OnlyOffice 文档重新解析事件失败，fileId={}, eventId={}", file.getFileId(), eventId, exception);
        }
    }

    /**
     * 判断 OnlyOffice 下载地址是否来自配置的 Document Server。
     */
    private boolean isAllowedDocumentServerUrl(URI uri) {
        return sameOrigin(uri, properties.getOnlyoffice().getPublicUrl())
                || sameOrigin(uri, properties.getOnlyoffice().getInternalUrl());
    }

    /**
     * 比较 URL 协议、主机和端口是否一致。
     */
    private boolean sameOrigin(URI actual, String allowedUrl) {
        if (!StringUtils.hasText(allowedUrl)) {
            return false;
        }
        URI allowed = URI.create(allowedUrl);
        int actualPort = actual.getPort() == -1 ? defaultPort(actual.getScheme()) : actual.getPort();
        int allowedPort = allowed.getPort() == -1 ? defaultPort(allowed.getScheme()) : allowed.getPort();
        return String.valueOf(actual.getScheme()).equalsIgnoreCase(String.valueOf(allowed.getScheme()))
                && String.valueOf(actual.getHost()).equalsIgnoreCase(String.valueOf(allowed.getHost()))
                && actualPort == allowedPort;
    }

    /**
     * 获取不含查询参数的 URL，用于日志排查时避免泄露 OnlyOffice token。
     */
    private String safePath(String url) {
        if (!StringUtils.hasText(url)) {
            return "";
        }
        URI uri = URI.create(url);
        int port = uri.getPort() == -1 ? defaultPort(uri.getScheme()) : uri.getPort();
        return uri.getScheme() + "://" + uri.getHost() + ":" + port + uri.getPath();
    }

    /**
     * 获取协议默认端口。
     */
    private int defaultPort(String scheme) {
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }

    /**
     * 生成 OnlyOffice document.key。
     */
    private String documentKey(DocumentFile file, int currentVersion) {
        String hash = file.getFileSha256() == null ? "nohash" : file.getFileSha256();
        return (file.getFileId() + "-" + currentVersion + "-" + hash).replaceAll("[^A-Za-z0-9._=-]", "_");
    }

    /**
     * 获取 OnlyOffice 文档类型。
     */
    private String documentType(String fileExt) {
        return "pptx".equals(fileExt) ? "slide" : "word";
    }

    /**
     * 获取文件内容类型。
     */
    private String contentType(String fileExt) {
        return switch (fileExt) {
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "txt" -> "text/plain;charset=UTF-8";
            default -> "application/octet-stream";
        };
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

    /**
     * 获取前端展示名，未填写时退回原始文件名。
     */
    private String displayName(DocumentFile file) {
        return file.getDisplayName() == null || file.getDisplayName().isBlank()
                ? file.getOriginalName()
                : file.getDisplayName();
    }

    /**
     * 构建回调基础地址。
     */
    private String buildCallbackBaseUrl() {
        return trimTrailingSlash(properties.getOnlyoffice().getCallbackBaseUrl());
    }

    /**
     * 构造 OnlyOffice 命令服务地址。
     */
    private String commandUrl(String path, String documentKey) {
        return trimTrailingSlash(properties.getOnlyoffice().getInternalUrl())
                + path
                + "?shardkey="
                + encode(documentKey);
    }

    /**
     * 去掉末尾斜杠。
     */
    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    /**
     * URL 编码 token。
     */
    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 获取字符串 claim。
     */
    private String claimString(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 获取 Long claim。
     */
    private Long claimLong(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    /**
     * 获取 Integer claim。
     */
    private Integer claimInteger(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    /**
     * OnlyOffice 成功响应。
     */
    private Map<String, Integer> callbackOk() {
        return Map.of("error", 0);
    }

    /**
     * OnlyOffice 失败响应。
     */
    private Map<String, Integer> callbackError() {
        return Map.of("error", 1);
    }

    /**
     * 字节数组转十六进制字符串。
     */
    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    /**
     * OnlyOffice 下载后的临时文件。
     */
    private record DownloadedOfficeFile(Path path, long size, String sha256) {
    }

    /**
     * OnlyOffice 保存落库后的版本结果。
     */
    private record SavedOfficeFileResult(int currentVersion, String contentHash) {
    }
}
