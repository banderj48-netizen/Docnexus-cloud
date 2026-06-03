package com.xyf.docnexus.file.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 分片上传初始化请求。
 */
@Data
public class MultipartInitRequest {
    @NotBlank(message = "不能为空")
    private String fileName;

    @NotNull(message = "不能为空")
    @Min(value = 1, message = "必须大于 0")
    private Long fileSize;

    private String mimeType;
    private String fileSha256;
    private Long chunkSize;
    private Integer totalChunks;
    private String knowledgeBaseId = "default";
}
