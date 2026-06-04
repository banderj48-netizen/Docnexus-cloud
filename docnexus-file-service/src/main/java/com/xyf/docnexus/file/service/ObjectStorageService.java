package com.xyf.docnexus.file.service;

import com.xyf.docnexus.file.dto.StoredObject;
import com.xyf.docnexus.file.entity.FileUploadChunk;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * 对象存储服务接口。
 */
public interface ObjectStorageService {

    /**
     * 上传正式原始文件。
     */
    StoredObject uploadOriginal(Long userId, String fileId, String fileExt, MultipartFile file);

    /**
     * 上传在线编辑生成的新正式文件版本。
     */
    StoredObject uploadEditorVersion(Long userId, String fileId, String fileExt, int versionNumber, byte[] content, String contentType);

    /**
     * 流式上传在线编辑生成的新正式文件版本。
     */
    StoredObject uploadEditorVersion(Long userId, String fileId, String fileExt, int versionNumber, InputStream inputStream, long size, String contentType);

    /**
     * 覆盖写入已有正式对象。
     */
    StoredObject overwriteObject(String bucketName, String objectKey, byte[] content, String contentType);

    /**
     * 流式覆盖写入已有正式对象。
     */
    StoredObject overwriteObject(String bucketName, String objectKey, InputStream inputStream, long size, String contentType);

    /**
     * 上传临时分片。
     */
    StoredObject uploadTempChunk(Long userId, String uploadId, Integer chunkIndex, MultipartFile chunk);

    /**
     * 合成临时分片为正式对象。
     */
    StoredObject composeOriginal(Long userId, String fileId, String fileExt, List<FileUploadChunk> chunks);

    /**
     * 读取正式对象流。
     */
    InputStream getObject(String bucketName, String objectKey);

    /**
     * 删除对象，删除失败由调用方记录日志即可。
     */
    void removeObject(String bucketName, String objectKey);

    /**
     * 删除临时分片对象。
     */
    void removeTempChunks(List<FileUploadChunk> chunks);
}
