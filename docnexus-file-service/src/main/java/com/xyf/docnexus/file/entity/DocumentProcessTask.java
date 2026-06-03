package com.xyf.docnexus.file.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档处理任务实体。
 *
 * <p>第一阶段只在上传成功后创建等待解析任务，真正解析由后续 document-service 或 Python Agent 接管。</p>
 */
@Data
public class DocumentProcessTask {
    private Long id;
    private String taskId;
    private String fileId;
    private Long userId;
    private String taskType;
    private String taskStatus;
    private String stage;
    private Integer progress;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
