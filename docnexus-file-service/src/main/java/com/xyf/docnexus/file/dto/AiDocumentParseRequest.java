package com.xyf.docnexus.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 调用 AI 服务解析文档的请求。
 *
 * <p>FileService 只传递文件元数据和对象存储位置，真实解析逻辑由 AI 服务后续实现。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiDocumentParseRequest {
    private String eventId;
    private String fileId;
    private Long userId;
    private Integer versionNumber;
    private String bucketName;
    private String objectKey;
    private String reason;
}
