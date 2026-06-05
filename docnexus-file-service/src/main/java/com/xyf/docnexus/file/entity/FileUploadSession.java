package com.xyf.docnexus.file.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件上传会话实体。
 *
 * <p>普通上传和分片上传都会创建会话，用于记录临时状态、失败原因和断点续传信息。</p>
 */
@Data
public class FileUploadSession {
    private Long id;
    private String uploadId;
    private Long userId;
    private String fileId;
    private String knowledgeBaseId;
    private String fileName;
    private String displayName;
    private Long fileSize;
    private String fileCategory;
    private String fileExt;
    private String mimeType;
    private String fileSha256;
    private String knowledgeSpaceCode;
    private String knowledgeSpaceName;
    private String businessCategoryCode;
    private String businessCategoryName;
    private String documentType;
    private String documentTagsJson;
    private String metadataDraftJson;
    private Long chunkSize;
    private Integer totalChunks;
    private Integer uploadedChunks;
    private String status;
    private String bucketName;
    private String objectKey;
    private String tempBucketName;
    private String tempPrefix;
    private String errorMessage;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
