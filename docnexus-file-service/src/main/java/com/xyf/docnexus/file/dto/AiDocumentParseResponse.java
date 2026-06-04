package com.xyf.docnexus.file.dto;

import lombok.Data;

/**
 * AI 文档解析接口响应。
 *
 * <p>当前只作为 OpenFeign 对接契约预留，AI 服务暂时可以返回 accepted=true 表示已接收。</p>
 */
@Data
public class AiDocumentParseResponse {
    private Boolean accepted;
    private String taskId;
    private String message;
}
