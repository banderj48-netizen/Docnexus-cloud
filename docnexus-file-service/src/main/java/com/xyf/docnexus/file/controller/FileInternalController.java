package com.xyf.docnexus.file.controller;

import com.xyf.docnexus.common.VO.ApiResponse;
import com.xyf.docnexus.common.log.BusinessOperationLog;
import com.xyf.docnexus.file.config.FileServiceProperties;
import com.xyf.docnexus.file.dto.DocumentParseCallbackRequest;
import com.xyf.docnexus.file.service.FileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件服务内部接口。
 *
 * <p>该接口只允许内部解析服务调用，用于回写文档解析成功或失败结果。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/files")
public class FileInternalController {

    private final FileService fileService;
    private final FileServiceProperties properties;

    /**
     * 回写文档解析结果。
     */
    @BusinessOperationLog(module = "文件服务", functionName = "解析回调", operationType = "UPDATE",
            operationName = "回写文档解析结果", triggerType = "INTERNAL_CALL", operationSource = "AI_SERVICE", userVisible = false)
    @PostMapping("/parse-result")
    public ApiResponse<Void> updateParseResult(@RequestHeader("X-Internal-Token") String internalToken,
                                               @Valid @RequestBody DocumentParseCallbackRequest request) {
        validateInternalToken(internalToken);
        fileService.updateParseResult(request);
        return ApiResponse.success("解析结果已回写", null);
    }

    /**
     * 校验内部调用 token，避免普通前端伪造解析结果。
     */
    private void validateInternalToken(String internalToken) {
        String expected = properties.getInternal().getCallbackToken();
        if (!StringUtils.hasText(expected) || "change-me".equals(expected) || !expected.equals(internalToken)) {
            throw new IllegalArgumentException("内部回调令牌无效");
        }
    }
}
