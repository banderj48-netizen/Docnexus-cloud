package com.xyf.docnexus.file.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 分片上传完成请求。
 */
@Data
public class MultipartCompleteRequest {
    @NotBlank(message = "不能为空")
    private String uploadId;
    private String fileSha256;
}
