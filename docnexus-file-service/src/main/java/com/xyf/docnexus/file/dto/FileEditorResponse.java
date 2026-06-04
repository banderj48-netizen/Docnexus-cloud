package com.xyf.docnexus.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文档编辑页详情返回对象。
 *
 * <p>前端通过该对象渲染真实文档内容、当前版本和保存状态。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileEditorResponse {
    private String fileId;
    private String originalName;
    private String fileExt;
    private Boolean editable;
    private Integer currentVersion;
    private String contentHtml;
    private String previewUrl;
    private String contentHash;
    private String parseStatus;
    private String indexStatus;
    private LocalDateTime updatedAt;
}
