package com.xyf.docnexus.file.service;

import com.xyf.docnexus.file.dto.FileEditorResponse;
import com.xyf.docnexus.file.dto.FileEditorSaveRequest;
import com.xyf.docnexus.file.dto.FileEditorSaveResponse;

/**
 * 文件在线编辑服务接口。
 */
public interface FileEditorService {

    /**
     * 打开文件编辑页。
     */
    FileEditorResponse openEditor(Long userId, String fileId);

    /**
     * 保存在线编辑内容并触发重新解析。
     */
    FileEditorSaveResponse saveContent(Long userId, String fileId, FileEditorSaveRequest request);
}
