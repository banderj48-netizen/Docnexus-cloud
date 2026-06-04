package com.xyf.docnexus.file.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 前端已上传文档列表展示对象。
 */
@Data
public class FileViewResponse {
    private String id;
    private String fileId;
    private String uploadId;
    private String name;
    private String type;
    private String fileExt;
    private Long fileSize;
    private String sizeText;
    private String timeText;
    private String uploadStatus;
    private String parseStatus;
    private String indexStatus;
    private String graphStatus;
    private String statusText;
    private String statusTone;
    private String knowledgeText;
    private String knowledgeTone;
    private String graphText;
    private String graphTone;
    private Integer progress;
    private String errorMessage;
    private Integer parseRetryCount;
    private Integer currentVersion;
    private Boolean editable;
    private LocalDateTime createdAt;
}
