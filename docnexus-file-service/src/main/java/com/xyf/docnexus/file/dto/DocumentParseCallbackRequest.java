package com.xyf.docnexus.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文档解析结果回调请求。
 *
 * <p>后续 AI 服务完成真实解析后，通过内部接口把解析结果回写到 FileService。</p>
 */
@Data
public class DocumentParseCallbackRequest {

    @NotBlank(message = "文件 ID 不能为空")
    private String fileId;

    @NotNull(message = "用户 ID 不能为空")
    private Long userId;

    private String taskId;

    @NotBlank(message = "解析状态不能为空")
    private String parseStatus;

    private String summary;
    private String keywordsJson;
    private String metadataJson;
    private String qualityJson;
    private Integer parseQualityScore;
    private Integer parentChunkCount;
    private Integer childChunkCount;
    private Integer assetCount;
    private String errorMessage;
}
