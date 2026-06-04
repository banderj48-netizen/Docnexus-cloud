package com.xyf.docnexus.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文档重新解析 MQ 事件。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentReparseEvent {
    private String eventId;
    private String fileId;
    private Long userId;
    private Integer versionNumber;
    private String bucketName;
    private String objectKey;
    private String reason;
    private LocalDateTime occurredAt;
}
