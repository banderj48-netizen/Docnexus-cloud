package com.xyf.docnexus.file.service;

/**
 * 在线编辑内容重新生成文件服务。
 */
public interface EditorFileRenderer {

    /**
     * 把前端安全 HTML 重新生成当前文件格式的二进制内容。
     */
    byte[] render(String fileExt, String contentHtml);

    /**
     * 获取生成文件的内容类型。
     */
    String contentType(String fileExt);
}
