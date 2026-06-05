package com.xyf.docnexus.file.dto;

import lombok.Data;

import java.util.List;

/**
 * AI 元信息预填响应。
 *
 * <p>当前先基于文件名和已知格式做轻量建议，后续可以替换为调用 AI Service 的真实内容解析结果。</p>
 */
@Data
public class AiMetadataSuggestResponse {
    private String displayName;
    private String knowledgeSpaceCode;
    private String knowledgeSpaceName;
    private String businessCategoryCode;
    private String businessCategoryName;
    private String documentType;
    private List<String> documentTags;
    private String sourceType;
    private String reason;
}
