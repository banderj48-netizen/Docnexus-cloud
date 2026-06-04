package com.xyf.docnexus.file.client;

import com.xyf.docnexus.file.dto.AiDocumentParseRequest;
import com.xyf.docnexus.file.dto.AiDocumentParseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * AI 服务文档解析 OpenFeign 客户端。
 *
 * <p>该客户端只定义调用契约，AI 服务的真实解析接口后续再实现。</p>
 */
@FeignClient(name = "docnexus-ai-service", url = "${docnexus.file.ai-service.base-url:http://127.0.0.1:8200}", path = "/api/agent")
public interface AiDocumentParseClient {

    /**
     * 请求 AI 服务开始解析文档。
     */
    @PostMapping("/documents/parse")
    AiDocumentParseResponse parseDocument(@RequestBody AiDocumentParseRequest request);
}
