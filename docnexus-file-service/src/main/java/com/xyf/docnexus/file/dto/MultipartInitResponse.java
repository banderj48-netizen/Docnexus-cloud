package com.xyf.docnexus.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分片上传初始化响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MultipartInitResponse {
    private String uploadId;
    private String fileId;
    private Long chunkSize;
    private Integer totalChunks;
    private List<Integer> uploadedChunks;
}
