package com.xyf.docnexus.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 可恢复上传会话响应。
 *
 * <p>前端重新选择同一个本地文件后，依据 uploadedChunkIndexes 跳过已上传分片。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecoverableUploadResponse {
    private FileViewResponse file;
    private String uploadId;
    private String fileName;
    private Long fileSize;
    private Long chunkSize;
    private Integer totalChunks;
    private Integer uploadedChunks;
    private List<Integer> uploadedChunkIndexes;
    private String status;
    private String errorMessage;
    private String metadataDraftJson;
}
