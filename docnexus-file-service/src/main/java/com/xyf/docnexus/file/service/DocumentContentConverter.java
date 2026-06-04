package com.xyf.docnexus.file.service;

import com.xyf.docnexus.file.dto.DocumentContentConversion;

import java.io.InputStream;

/**
 * 文档内容转换服务。
 */
public interface DocumentContentConverter {

    /**
     * 从真实文件流中抽取可展示内容。
     */
    DocumentContentConversion convert(String fileExt, InputStream inputStream);

    /**
     * 判断文件扩展名是否支持在线编辑保存。
     */
    boolean editable(String fileExt);
}
