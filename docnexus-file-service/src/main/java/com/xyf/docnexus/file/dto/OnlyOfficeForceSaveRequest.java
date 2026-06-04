package com.xyf.docnexus.file.dto;

import lombok.Data;

/**
 * OnlyOffice 手动强制保存请求。
 *
 * <p>前端打开编辑器时保存当前版本和 documentKey，手动保存时带回，
 * 后端据此做乐观校验，避免旧页面把新版本文档错误保存。</p>
 */
@Data
public class OnlyOfficeForceSaveRequest {
    private Integer currentVersion;
    private String documentKey;
}
