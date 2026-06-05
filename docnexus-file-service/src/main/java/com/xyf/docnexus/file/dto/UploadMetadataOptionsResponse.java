package com.xyf.docnexus.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 上传元信息表单选项响应。
 *
 * <p>前端通过该接口获取两级知识域、文档类型和来源类型，避免页面写死后端不认识的分类编码。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadMetadataOptionsResponse {
    private List<KnowledgeSpaceOption> knowledgeSpaces;
    private List<OptionItem> documentTypes;
    private List<OptionItem> sourceTypes;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KnowledgeSpaceOption {
        private String code;
        private String name;
        private List<OptionItem> categories;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptionItem {
        private String code;
        private String name;
    }
}
