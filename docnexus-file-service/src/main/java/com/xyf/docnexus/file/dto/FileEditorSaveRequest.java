package com.xyf.docnexus.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文档编辑页保存请求。
 */
@Data
public class FileEditorSaveRequest {
    @NotNull(message = "当前版本不能为空")
    private Integer currentVersion;

    @NotBlank(message = "文档内容不能为空")
    private String contentHtml;

    private String contentHash;
}
