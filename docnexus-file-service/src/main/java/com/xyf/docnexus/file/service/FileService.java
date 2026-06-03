package com.xyf.docnexus.file.service;

import com.xyf.docnexus.common.VO.PageResponse;
import com.xyf.docnexus.file.dto.*;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件业务服务接口。
 */
public interface FileService {

    /**
     * 查询已上传文档列表。
     */
    PageResponse<FileViewResponse> list(Long userId, FileListRequest request);

    /**
     * 普通上传文件。
     */
    FileUploadResponse upload(Long userId, String knowledgeBaseId, MultipartFile file);

    /**
     * 初始化分片上传。
     */
    MultipartInitResponse initMultipart(Long userId, MultipartInitRequest request);

    /**
     * 上传单个分片。
     */
    UploadStatusResponse uploadChunk(Long userId, String uploadId, Integer chunkIndex, MultipartFile chunk);

    /**
     * 完成分片上传。
     */
    FileUploadResponse completeMultipart(Long userId, MultipartCompleteRequest request);

    /**
     * 查询上传会话状态。
     */
    UploadStatusResponse status(Long userId, String uploadId);

    /**
     * 取消上传会话。
     */
    void cancel(Long userId, String uploadId);

    /**
     * 丢弃失败上传项。
     */
    void discardFailed(Long userId, UploadFailureRequest request);

    /**
     * 生成失败上传项清单，前端据此重新排队上传原文件。
     */
    java.util.List<FileViewResponse> retryFailed(Long userId, UploadFailureRequest request);

    /**
     * 标记用户离开页面导致的可恢复上传中断，并清理失败上传项。
     */
    void interruptUploads(Long userId, UploadFailureRequest request);

    /**
     * 清理当前用户失败上传项。
     */
    void clearFailedUploads(Long userId);

    /**
     * 查询当前用户可恢复上传会话。
     */
    java.util.List<RecoverableUploadResponse> recoverableUploads(Long userId);

    /**
     * 下载或预览文件。
     */
    ResponseEntity<InputStreamResource> download(Long userId, String fileId, boolean inline);

    /**
     * 删除文件。
     */
    void delete(Long userId, String fileId);
}
