package com.xyf.docnexus.file.dto;

import lombok.Data;

import java.util.List;

/**
 * 上传阶段的文档业务元信息。
 *
 * <p>该结构只描述资料归类、展示名和学生场景字段，不包含文件二进制内容；普通上传和分片上传共用同一份元信息。</p>
 */
@Data
public class DocumentUploadMetadata {
    private String displayName;
    private String knowledgeSpaceCode;
    private String knowledgeSpaceName;
    private String businessCategoryCode;
    private String businessCategoryName;
    private String documentType;
    private List<String> documentTags;
    private String courseName;
    private String projectName;
    private String termName;
    private String sourceType;
    private String note;
}
