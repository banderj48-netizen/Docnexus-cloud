package com.xyf.docnexus.file.controller;

import com.xyf.docnexus.common.VO.ApiResponse;
import com.xyf.docnexus.common.log.BusinessOperationLog;
import com.xyf.docnexus.file.dto.OnlyOfficeCallbackRequest;
import com.xyf.docnexus.file.dto.OnlyOfficeConfigResponse;
import com.xyf.docnexus.file.dto.OnlyOfficeForceSaveRequest;
import com.xyf.docnexus.file.dto.OnlyOfficeForceSaveResponse;
import com.xyf.docnexus.file.service.OnlyOfficeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * OnlyOffice 在线编辑接口。
 *
 * <p>浏览器获取 config 仍需要网关注入用户身份；source 和 callback 由 OnlyOffice 访问，使用独立 token 验签。</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files")
public class FileOnlyOfficeController {

    private final OnlyOfficeService onlyOfficeService;

    /**
     * 获取 OnlyOffice 编辑器初始化配置。
     */
    @BusinessOperationLog(module = "文件服务", functionName = "打开 OnlyOffice 编辑器", operationType = "QUERY",
            operationName = "打开原样编辑文档", triggerType = "USER_ACTION", operationSource = "FRONTEND", userVisible = true)
    @GetMapping("/{fileId}/onlyoffice/config")
    public ApiResponse<OnlyOfficeConfigResponse> config(@RequestHeader("X-User-Id") Long userId,
                                                        @RequestHeader(value = "X-Username", required = false) String username,
                                                        @PathVariable("fileId") String fileId) {
        log.info("OnlyOffice 配置接口收到请求，fileId={}, userId={}, usernamePresent={}",
                fileId, userId, username != null && !username.isBlank());
        OnlyOfficeConfigResponse response = onlyOfficeService.buildConfig(userId, username, fileId);
        log.info("OnlyOffice 配置接口返回，fileId={}, fileExt={}, currentVersion={}, documentKey={}",
                response.getFileId(), response.getFileExt(), response.getCurrentVersion(), response.getDocumentKey());
        return ApiResponse.success(response);
    }

    /**
     * OnlyOffice 拉取当前 MinIO 文件源。
     */
    @GetMapping("/{fileId}/onlyoffice/source")
    public ResponseEntity<InputStreamResource> source(@PathVariable("fileId") String fileId,
                                                      @RequestParam("token") String token) {
        log.info("OnlyOffice 源文件接口收到请求，fileId={}, tokenPresent={}",
                fileId, token != null && !token.isBlank());
        ResponseEntity<InputStreamResource> response = onlyOfficeService.source(fileId, token);
        log.info("OnlyOffice 源文件接口返回，fileId={}, httpStatus={}, contentLength={}",
                fileId, response.getStatusCode().value(), response.getHeaders().getContentLength());
        return response;
    }

    /**
     * 用户点击“手动保存”后触发 OnlyOffice 强制保存。
     */
    @BusinessOperationLog(module = "文件服务", functionName = "手动保存 OnlyOffice 文档", operationType = "SAVE",
            operationName = "手动保存原样编辑文档", triggerType = "USER_ACTION", operationSource = "FRONTEND", userVisible = true)
    @PostMapping("/{fileId}/onlyoffice/forcesave")
    public ApiResponse<OnlyOfficeForceSaveResponse> forceSave(@RequestHeader("X-User-Id") Long userId,
                                                              @PathVariable("fileId") String fileId,
                                                              @RequestBody(required = false) OnlyOfficeForceSaveRequest request) {
        log.info("OnlyOffice 手动保存接口收到请求，fileId={}, userId={}, version={}",
                fileId, userId, request == null ? null : request.getCurrentVersion());
        OnlyOfficeForceSaveResponse response = onlyOfficeService.forceSave(userId, fileId, request);
        log.info("OnlyOffice 手动保存接口返回，fileId={}, userId={}, saved={}, version={}",
                fileId, userId, response.getSaved(), response.getCurrentVersion());
        return ApiResponse.success(response.getMessage(), response);
    }

    /**
     * OnlyOffice 保存回调。
     */
    @PostMapping("/{fileId}/onlyoffice/callback")
    public Map<String, Integer> callback(@PathVariable("fileId") String fileId,
                                         @RequestParam("token") String token,
                                         @RequestBody(required = false) OnlyOfficeCallbackRequest request) {
        log.info("OnlyOffice 保存回调收到请求，fileId={}, status={}, key={}, hasDownloadUrl={}, tokenPresent={}",
                fileId,
                request == null ? null : request.getStatus(),
                request == null ? null : request.getKey(),
                request != null && request.getUrl() != null && !request.getUrl().isBlank(),
                token != null && !token.isBlank());
        Map<String, Integer> response = onlyOfficeService.callback(fileId, token, request);
        log.info("OnlyOffice 保存回调返回，fileId={}, status={}, error={}",
                fileId, request == null ? null : request.getStatus(), response.get("error"));
        return response;
    }
}
