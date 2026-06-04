package com.xyf.docnexus.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档编辑页保存结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileEditorSaveResponse {
    private String fileId;
    private Integer currentVersion;
    private String contentHash;
    private String parseStatus;
    private String indexStatus;
}
