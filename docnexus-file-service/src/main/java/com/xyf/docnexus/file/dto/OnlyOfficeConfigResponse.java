package com.xyf.docnexus.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * OnlyOffice 编辑器初始化响应。
 *
 * <p>前端用 documentServerApiUrl 加载 OnlyOffice API，再把 config 传入 DocsAPI.DocEditor。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnlyOfficeConfigResponse {
    private String fileId;
    private String originalName;
    private String fileExt;
    private Boolean editable;
    private Integer currentVersion;
    private String documentKey;
    private String documentServerApiUrl;
    private Map<String, Object> config;
    private Map<String, Object> diagnostics;
    private String parseStatus;
    private String indexStatus;
    private LocalDateTime updatedAt;
}
