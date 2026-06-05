package com.xyf.docnexus.file.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档文件元数据实体。
 *
 * <p>对应 document_file 表，只保存文件索引、存储位置和后续解析状态。</p>
 */
@Data
public class DocumentFile {
    private Long id;
    private String fileId;
    private Long userId;
    private String knowledgeBaseId;
    private String knowledgeSpaceCode;
    private String knowledgeSpaceName;
    private String businessCategoryCode;
    private String businessCategoryName;
    private String originalName;
    private String displayName;
    private String fileCategory;
    private String fileExt;
    private String mimeType;
    private Long fileSize;
    private String fileSha256;
    private String documentType;
    private String documentTagsJson;
    private String courseName;
    private String projectName;
    private String termName;
    private String sourceType;
    private String storageType;
    private String bucketName;
    private String objectKey;
    private String uploadStatus;
    private String parseStatus;
    private String indexStatus;
    private String graphStatus;
    private String summary;
    private String keywordsJson;
    private String metadataStatus;
    private String aiMetadataJson;
    private Integer parseQualityScore;
    private Integer parentChunkCount;
    private Integer childChunkCount;
    private Integer assetCount;
    private String errorMessage;
    private Integer parseRetryCount;
    private Integer currentVersion;
    private Integer editable;
    private String contentHash;
    private LocalDateTime lastSavedAt;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
