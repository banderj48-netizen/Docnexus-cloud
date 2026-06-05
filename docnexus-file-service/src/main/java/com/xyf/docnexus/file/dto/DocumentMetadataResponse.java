package com.xyf.docnexus.file.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 文档元信息响应。
 *
 * <p>前端编辑抽屉和上传队列都使用该结构展示当前保存的用户元信息与 AI 建议。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DocumentMetadataResponse extends DocumentMetadataRequest {
    private String fileId;
    private String originalName;
    private String metadataStatus;
    private String aiMetadataJson;
    private List<String> suggestedTags;
}
