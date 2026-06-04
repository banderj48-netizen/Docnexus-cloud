package com.xyf.docnexus.file.service;

import com.xyf.docnexus.file.entity.DocumentFile;

/**
 * 文档文件元数据查询服务。
 *
 * <p>该服务统一封装“先查 Redis，未命中再查 MySQL”的逻辑，
 * 避免下载、预览、编辑和 OnlyOffice 链路重复访问数据库。</p>
 */
public interface DocumentFileLookupService {

    /**
     * 查询并校验当前用户文件，文件不存在、已删除或不属于当前用户时抛出业务异常。
     */
    DocumentFile requireFile(Long userId, String fileId);

    /**
     * 写入单文件元数据缓存。
     */
    void cacheFile(DocumentFile file);

    /**
     * 在事务提交后写入单文件元数据缓存；没有事务时立即写入。
     */
    void cacheFileAfterCommit(DocumentFile file);
}
