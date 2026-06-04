package com.xyf.docnexus.file.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档内容保存覆盖日志实体。
 *
 * <p>保存时记录新旧对象位置和版本号，便于审计和排查 MinIO 覆盖链路。</p>
 */
@Data
public class DocumentContentRevisionLog {
    private Long id;
    private String eventId;
    private String fileId;
    private Long userId;
    private Integer oldVersion;
    private Integer newVersion;
    private String oldObjectKey;
    private String newObjectKey;
    private String contentHash;
    private LocalDateTime createdAt;
}
