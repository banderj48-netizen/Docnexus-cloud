package com.xyf.docnexus.file.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 文档元信息保存请求。
 *
 * <p>用户可以在上传成功后继续补充资料分类；AI 后续解析时只补空字段，不覆盖用户主动保存的内容。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DocumentMetadataRequest extends DocumentUploadMetadata {
    private String title;
    private List<String> authors;
    private String institution;
    private String journal;
    private String conferenceName;
    private String publisher;
    private Integer publishYear;
    private String doi;
    private String isbn;
    private String abstractText;
    private Integer referenceCount;
    private String assignmentSubject;
    private String reportType;
    private String requirementType;
    private String formPurpose;
}
