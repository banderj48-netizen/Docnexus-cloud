package com.xyf.docnexus.file.dto;

import lombok.Data;

/**
 * 文件列表查询参数。
 */
@Data
public class FileListRequest {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String knowledgeBaseId = "default";
}
