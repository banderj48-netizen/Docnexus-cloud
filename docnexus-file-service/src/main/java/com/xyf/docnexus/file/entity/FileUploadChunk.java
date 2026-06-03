package com.xyf.docnexus.file.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 上传分片实体。
 *
 * <p>每个分片在 MinIO 临时桶中的位置会记录到该表，重复上传同一分片时按唯一键覆盖。</p>
 */
@Data
public class FileUploadChunk {
    private Long id;
    private String uploadId;
    private Integer chunkIndex;
    private Long chunkSize;
    private String chunkSha256;
    private String bucketName;
    private String objectKey;
    private String status;
    private LocalDateTime createdAt;
}
