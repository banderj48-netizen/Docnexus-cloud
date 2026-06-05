package com.xyf.docnexus.file.controller;

import com.xyf.docnexus.common.VO.ApiResponse;
import com.xyf.docnexus.common.VO.PageResponse;
import com.xyf.docnexus.common.log.BusinessOperationLog;
import com.xyf.docnexus.file.dto.*;
import com.xyf.docnexus.file.service.FileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件服务对外接口。
 *
 * <p>所有接口都通过网关 `/api/files/**` 访问，用户身份只信任网关注入的 `X-User-Id`。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    /**
     * 查询已上传文档列表。
     */
    @BusinessOperationLog(module = "文件服务", functionName = "查询文档列表", operationType = "QUERY",
            operationName = "查询已上传文档", triggerType = "AUTO_QUERY", operationSource = "FRONTEND", userVisible = false)
    @GetMapping("/list")
    public ApiResponse<PageResponse<FileViewResponse>> list(@RequestHeader("X-User-Id") Long userId,
                                                            FileListRequest request) {
        return ApiResponse.success(fileService.list(userId, request));
    }

    /**
     * 普通上传文件，适用于小于 5MB 的文件。
     */
    @BusinessOperationLog(module = "文件服务", functionName = "上传文档", operationType = "UPLOAD",
            operationName = "上传文档", triggerType = "USER_ACTION", operationSource = "FRONTEND", userVisible = true)
    @PostMapping("/upload")
    public ApiResponse<FileUploadResponse> upload(@RequestHeader("X-User-Id") Long userId,
                                                  @RequestParam(value = "knowledgeBaseId", defaultValue = "default") String knowledgeBaseId,
                                                  @RequestParam(value = "metadataJson", required = false) String metadataJson,
                                                  @RequestParam("file") MultipartFile file) {
        return ApiResponse.success("上传成功", fileService.upload(userId, knowledgeBaseId, file, metadataJson));
    }

    /**
     * 初始化分片上传，适用于 5MB 到 200MB 的文件。
     */
    @BusinessOperationLog(module = "文件服务", functionName = "初始化分片上传", operationType = "UPLOAD",
            operationName = "初始化分片上传", triggerType = "USER_ACTION", operationSource = "FRONTEND", userVisible = true)
    @PostMapping("/multipart/init")
    public ApiResponse<MultipartInitResponse> initMultipart(@RequestHeader("X-User-Id") Long userId,
                                                            @Valid @RequestBody MultipartInitRequest request) {
        return ApiResponse.success(fileService.initMultipart(userId, request));
    }

    /**
     * 上传单个分片。
     */
    @BusinessOperationLog(module = "文件服务", functionName = "上传文件分片", operationType = "UPLOAD",
            operationName = "上传文件分片", triggerType = "USER_ACTION", operationSource = "FRONTEND", userVisible = false)
    @PostMapping("/multipart/chunk")
    public ApiResponse<UploadStatusResponse> uploadChunk(@RequestHeader("X-User-Id") Long userId,
                                                         @RequestParam("uploadId") String uploadId,
                                                         @RequestParam("chunkIndex") Integer chunkIndex,
                                                         @RequestParam("chunk") MultipartFile chunk) {
        return ApiResponse.success(fileService.uploadChunk(userId, uploadId, chunkIndex, chunk));
    }

    /**
     * 完成分片上传并合并为正式文件。
     */
    @BusinessOperationLog(module = "文件服务", functionName = "完成分片上传", operationType = "UPLOAD",
            operationName = "完成分片上传", triggerType = "USER_ACTION", operationSource = "FRONTEND", userVisible = true)
    @PostMapping("/multipart/complete")
    public ApiResponse<FileUploadResponse> completeMultipart(@RequestHeader("X-User-Id") Long userId,
                                                             @Valid @RequestBody MultipartCompleteRequest request) {
        return ApiResponse.success("上传成功", fileService.completeMultipart(userId, request));
    }

    /**
     * 查询上传会话状态。
     */
    @BusinessOperationLog(module = "文件服务", functionName = "查询上传状态", operationType = "QUERY",
            operationName = "查询上传状态", triggerType = "SYSTEM_POLLING", operationSource = "FRONTEND", userVisible = false)
    @GetMapping("/multipart/status/{uploadId}")
    public ApiResponse<UploadStatusResponse> multipartStatus(@RequestHeader("X-User-Id") Long userId,
                                                             @PathVariable("uploadId") String uploadId) {
        return ApiResponse.success(fileService.status(userId, uploadId));
    }

    /**
     * 取消上传会话。
     */
    @BusinessOperationLog(module = "文件服务", functionName = "取消上传", operationType = "CANCEL",
            operationName = "取消上传", triggerType = "USER_ACTION", operationSource = "FRONTEND", userVisible = true)
    @PostMapping("/multipart/cancel/{uploadId}")
    public ApiResponse<Void> cancelMultipart(@RequestHeader("X-User-Id") Long userId,
                                             @PathVariable("uploadId") String uploadId) {
        fileService.cancel(userId, uploadId);
        return ApiResponse.success("已取消上传", null);
    }

    /**
     * 丢弃失败上传项。
     */
    @BusinessOperationLog(module = "文件服务", functionName = "取消失败上传", operationType = "CANCEL",
            operationName = "取消失败上传", triggerType = "USER_ACTION", operationSource = "FRONTEND", userVisible = true)
    @PostMapping("/uploads/discard-failed")
    public ApiResponse<Void> discardFailed(@RequestHeader("X-User-Id") Long userId,
                                           @RequestBody(required = false) UploadFailureRequest request) {
        fileService.discardFailed(userId, request);
        return ApiResponse.success("已清理上传失败文件", null);
    }

    /**
     * 查询失败上传项，前端拿到后重新排队原文件。
     */
    @BusinessOperationLog(module = "文件服务", functionName = "重新上传", operationType = "RETRY",
            operationName = "重新上传失败文档", triggerType = "USER_ACTION", operationSource = "FRONTEND", userVisible = true)
    @PostMapping("/uploads/retry-failed")
    public ApiResponse<List<FileViewResponse>> retryFailed(@RequestHeader("X-User-Id") Long userId,
                                                           @RequestBody(required = false) UploadFailureRequest request) {
        return ApiResponse.success(fileService.retryFailed(userId, request));
    }

    /**
     * 页面离开或刷新时标记上传中断，并清理失败上传项。
     */
    @BusinessOperationLog(module = "文件服务", functionName = "上传中断", operationType = "UPDATE",
            operationName = "标记上传中断", triggerType = "AUTO_QUERY", operationSource = "FRONTEND", userVisible = false)
    @PostMapping("/uploads/interrupt")
    public ApiResponse<Void> interruptUploads(@RequestHeader("X-User-Id") Long userId,
                                              @RequestBody(required = false) UploadFailureRequest request) {
        fileService.interruptUploads(userId, request);
        return ApiResponse.success("已记录上传中断", null);
    }

    /**
     * 查询当前用户可恢复上传会话。
     */
    @BusinessOperationLog(module = "文件服务", functionName = "查询可恢复上传", operationType = "QUERY",
            operationName = "查询可恢复上传", triggerType = "AUTO_QUERY", operationSource = "FRONTEND", userVisible = false)
    @GetMapping("/uploads/recoverable")
    public ApiResponse<List<RecoverableUploadResponse>> recoverableUploads(@RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.success(fileService.recoverableUploads(userId));
    }

    /**
     * 查询上传元信息表单选项。
     */
    @GetMapping("/upload-metadata/options")
    public ApiResponse<UploadMetadataOptionsResponse> uploadMetadataOptions() {
        return ApiResponse.success(fileService.uploadMetadataOptions());
    }

    /**
     * 查询单个文档元信息。
     */
    @GetMapping("/{fileId}/metadata")
    public ApiResponse<DocumentMetadataResponse> getMetadata(@RequestHeader("X-User-Id") Long userId,
                                                             @PathVariable("fileId") String fileId) {
        return ApiResponse.success(fileService.getMetadata(userId, fileId));
    }

    /**
     * 保存单个文档元信息。
     */
    @PutMapping("/{fileId}/metadata")
    public ApiResponse<DocumentMetadataResponse> saveMetadata(@RequestHeader("X-User-Id") Long userId,
                                                              @PathVariable("fileId") String fileId,
                                                              @RequestBody DocumentMetadataRequest request) {
        return ApiResponse.success("元信息已保存", fileService.saveMetadata(userId, fileId, request));
    }

    /**
     * 预留 AI 元信息填写接口。
     */
    @PostMapping("/{fileId}/metadata/ai-suggest")
    public ApiResponse<AiMetadataSuggestResponse> suggestMetadata(@RequestHeader("X-User-Id") Long userId,
                                                                  @PathVariable("fileId") String fileId) {
        return ApiResponse.success(fileService.suggestMetadata(userId, fileId));
    }

    /**
     * 清理当前用户失败上传项。
     */
    @BusinessOperationLog(module = "文件服务", functionName = "清理失败上传", operationType = "DELETE",
            operationName = "清理失败上传", triggerType = "AUTO_QUERY", operationSource = "FRONTEND", userVisible = false)
    @PostMapping("/uploads/clear-failed")
    public ApiResponse<Void> clearFailedUploads(@RequestHeader("X-User-Id") Long userId) {
        fileService.clearFailedUploads(userId);
        return ApiResponse.success("已清理上传失败文件", null);
    }

    /**
     * 下载文件。
     */
    @BusinessOperationLog(module = "文件服务", functionName = "下载文档", operationType = "DOWNLOAD",
            operationName = "下载文档", triggerType = "USER_ACTION", operationSource = "FRONTEND", userVisible = true)
    @GetMapping("/download/{fileId}")
    public ResponseEntity<InputStreamResource> download(@RequestHeader("X-User-Id") Long userId,
                                                        @PathVariable("fileId") String fileId) {
        return fileService.download(userId, fileId, false);
    }

    /**
     * 预览文件。
     */
    @BusinessOperationLog(module = "文件服务", functionName = "预览文档", operationType = "QUERY",
            operationName = "预览文档", triggerType = "USER_ACTION", operationSource = "FRONTEND", userVisible = true)
    @GetMapping("/preview/{fileId}")
    public ResponseEntity<InputStreamResource> preview(@RequestHeader("X-User-Id") Long userId,
                                                       @PathVariable("fileId") String fileId) {
        return fileService.download(userId, fileId, true);
    }

    /**
     * 删除文件。
     */
    @BusinessOperationLog(module = "文件服务", functionName = "删除文档", operationType = "DELETE",
            operationName = "删除文档", triggerType = "USER_ACTION", operationSource = "FRONTEND", userVisible = true)
    @DeleteMapping("/{fileId}")
    public ApiResponse<Void> delete(@RequestHeader("X-User-Id") Long userId,
                                    @PathVariable("fileId") String fileId) {
        fileService.delete(userId, fileId);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 用户手动提交文件解析或重新解析。
     */
    @BusinessOperationLog(module = "文件服务", functionName = "文档解析", operationType = "UPDATE",
            operationName = "提交文档解析", triggerType = "USER_ACTION", operationSource = "FRONTEND", userVisible = true)
    @PostMapping("/{fileId}/reindex")
    public ApiResponse<Void> reindex(@RequestHeader("X-User-Id") Long userId,
                                     @PathVariable("fileId") String fileId) {
        fileService.reindex(userId, fileId);
        return ApiResponse.success("已提交解析", null);
    }
}
