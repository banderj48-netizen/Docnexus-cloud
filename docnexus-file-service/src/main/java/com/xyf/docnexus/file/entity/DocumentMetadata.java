package com.xyf.docnexus.file.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 文档详细元数据实体。
 *
 * <p>保存用户填写或 AI 解析出的论文、作业、申请表、报告等结构化字段，避免把学生场景的业务元信息挤在文件主表里。</p>
 */
@Data
public class DocumentMetadata {
    private Long id;
    private String fileId;
    private Long userId;
    private String title;
    private String authorsJson;
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
    private String extractionSource;
    private BigDecimal confidence;
    private String evidenceJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
