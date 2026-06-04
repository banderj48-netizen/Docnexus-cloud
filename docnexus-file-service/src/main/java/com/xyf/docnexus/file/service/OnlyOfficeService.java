package com.xyf.docnexus.file.service;

import com.xyf.docnexus.file.dto.OnlyOfficeCallbackRequest;
import com.xyf.docnexus.file.dto.OnlyOfficeConfigResponse;
import com.xyf.docnexus.file.dto.OnlyOfficeForceSaveRequest;
import com.xyf.docnexus.file.dto.OnlyOfficeForceSaveResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * OnlyOffice 在线编辑服务。
 */
public interface OnlyOfficeService {

    /**
     * 生成 OnlyOffice 编辑器配置。
     */
    OnlyOfficeConfigResponse buildConfig(Long userId, String username, String fileId);

    /**
     * 通过 OnlyOffice 签名 token 读取当前文件源。
     */
    ResponseEntity<InputStreamResource> source(String fileId, String token);

    /**
     * 处理 OnlyOffice 保存回调。
     */
    Map<String, Integer> callback(String fileId, String token, OnlyOfficeCallbackRequest request);

    /**
     * 用户点击手动保存时触发 OnlyOffice forcesave 并等待回调覆盖 MinIO。
     */
    OnlyOfficeForceSaveResponse forceSave(Long userId, String fileId, OnlyOfficeForceSaveRequest request);
}
