package com.xyf.docnexus.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 上传会话状态响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadStatusResponse {
    private String uploadId;
    private String status;
    private Integer uploadedChunks;
    private Integer totalChunks;
    private List<Integer> uploadedChunkIndexes;
    private String errorMessage;
}
