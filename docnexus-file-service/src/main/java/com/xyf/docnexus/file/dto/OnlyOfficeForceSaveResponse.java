package com.xyf.docnexus.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OnlyOffice 手动强制保存响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnlyOfficeForceSaveResponse {
    private Boolean saved;
    private Integer currentVersion;
    private String contentHash;
    private String message;
}
