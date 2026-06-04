package com.xyf.docnexus.file.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档在线查看内容快照实体。
 *
 * <p>该表只保存当前文件版本对应的安全 HTML 和纯文本，避免每次打开页面都重复从 MinIO 抽取内容。</p>
 */
@Data
public class DocumentFileContent {
    private Long id;
    private String fileId;
    private Long userId;
    private Integer versionNumber;
    private String contentFormat;
    private String contentHtml;
    private String plainText;
    private String contentHash;
    private String sourceBucket;
    private String sourceObjectKey;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
