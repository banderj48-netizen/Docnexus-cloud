package com.xyf.docnexus.file.controller;

import com.xyf.docnexus.common.VO.ApiResponse;
import com.xyf.docnexus.common.log.BusinessOperationLog;
import com.xyf.docnexus.file.dto.FileEditorResponse;
import com.xyf.docnexus.file.dto.FileEditorSaveRequest;
import com.xyf.docnexus.file.dto.FileEditorSaveResponse;
import com.xyf.docnexus.file.service.FileEditorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 文件在线编辑接口。
 *
 * <p>所有接口只信任网关注入的 X-User-Id，不接收前端传入 userId。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files")
public class FileEditorController {

    private final FileEditorService fileEditorService;

    /**
     * 打开文档编辑页，返回真实抽取内容或 PDF 预览地址。
     */
    @BusinessOperationLog(module = "文件服务", functionName = "打开文档编辑页", operationType = "QUERY",
            operationName = "打开文档", triggerType = "USER_ACTION", operationSource = "FRONTEND", userVisible = true)
    @GetMapping("/{fileId}/editor")
    public ApiResponse<FileEditorResponse> openEditor(@RequestHeader("X-User-Id") Long userId,
                                                      @PathVariable("fileId") String fileId) {
        return ApiResponse.success(fileEditorService.openEditor(userId, fileId));
    }

    /**
     * 保存在线编辑内容并触发重新解析。
     */
    @BusinessOperationLog(module = "文件服务", functionName = "保存文档编辑内容", operationType = "UPDATE",
            operationName = "手动保存文档", triggerType = "USER_ACTION", operationSource = "FRONTEND", userVisible = true)
    @PutMapping("/{fileId}/editor/content")
    public ApiResponse<FileEditorSaveResponse> saveContent(@RequestHeader("X-User-Id") Long userId,
                                                           @PathVariable("fileId") String fileId,
                                                           @Valid @RequestBody FileEditorSaveRequest request) {
        return ApiResponse.success("保存成功", fileEditorService.saveContent(userId, fileId, request));
    }
}
