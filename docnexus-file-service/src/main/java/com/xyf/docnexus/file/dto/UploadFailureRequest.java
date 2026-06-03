package com.xyf.docnexus.file.dto;

import lombok.Data;

import java.util.List;

/**
 * 上传失败文件统一处理请求。
 */
@Data
public class UploadFailureRequest {
    private List<String> uploadIds;
}
