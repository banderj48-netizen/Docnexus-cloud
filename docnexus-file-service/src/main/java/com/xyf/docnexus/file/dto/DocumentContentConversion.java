package com.xyf.docnexus.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档内容转换结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentContentConversion {
    private String contentFormat;
    private String contentHtml;
    private String plainText;
    private String contentHash;
    private boolean editable;
}
