package com.xyf.docnexus.file.service.impl;

import com.xyf.docnexus.file.config.FileServiceProperties;
import com.xyf.docnexus.file.dto.StoredObject;
import com.xyf.docnexus.file.entity.FileUploadChunk;
import com.xyf.docnexus.file.service.ObjectStorageService;
import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

/**
 * MinIO 对象存储服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioObjectStorageService implements ObjectStorageService {

    private final MinioClient minioClient;
    private final FileServiceProperties properties;

    /**
     * 上传正式原始文件。
     */
    @Override
    public StoredObject uploadOriginal(Long userId, String fileId, String fileExt, MultipartFile file) {
        String bucket = properties.getMinio().getOriginalBucket();
        String objectKey = originalObjectKey(userId, fileId, fileExt);
        try {
            ensureBucket(bucket);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            return new StoredObject(bucket, objectKey);
        } catch (Exception exception) {
            throw new IllegalStateException("上传文件到 MinIO 失败", exception);
        }
    }

    /**
     * 上传临时分片。
     */
    @Override
    public StoredObject uploadTempChunk(Long userId, String uploadId, Integer chunkIndex, MultipartFile chunk) {
        String bucket = properties.getMinio().getTempBucket();
        String objectKey = "users/" + userId + "/multipart/" + uploadId + "/" + chunkIndex + ".part";
        try {
            ensureBucket(bucket);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(chunk.getInputStream(), chunk.getSize(), -1)
                    .contentType(chunk.getContentType())
                    .build());
            return new StoredObject(bucket, objectKey);
        } catch (Exception exception) {
            throw new IllegalStateException("上传分片到 MinIO 失败", exception);
        }
    }

    /**
     * 合成临时分片为正式对象。
     */
    @Override
    public StoredObject composeOriginal(Long userId, String fileId, String fileExt, List<FileUploadChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            throw new IllegalArgumentException("没有可合并的分片");
        }
        String bucket = properties.getMinio().getOriginalBucket();
        String objectKey = originalObjectKey(userId, fileId, fileExt);
        try {
            ensureBucket(bucket);
            List<ComposeSource> sources = chunks.stream()
                    .map(chunk -> ComposeSource.builder()
                            .bucket(chunk.getBucketName())
                            .object(chunk.getObjectKey())
                            .build())
                    .toList();
            minioClient.composeObject(ComposeObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .sources(sources)
                    .build());
            return new StoredObject(bucket, objectKey);
        } catch (Exception exception) {
            throw new IllegalStateException("合并 MinIO 分片失败", exception);
        }
    }

    /**
     * 读取正式对象流。
     */
    @Override
    public InputStream getObject(String bucketName, String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            throw new IllegalStateException("读取 MinIO 文件失败", exception);
        }
    }

    /**
     * 删除对象。
     */
    @Override
    public void removeObject(String bucketName, String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            log.warn("删除 MinIO 对象失败，bucket={}, objectKey={}", bucketName, objectKey, exception);
        }
    }

    /**
     * 删除临时分片对象。
     */
    @Override
    public void removeTempChunks(List<FileUploadChunk> chunks) {
        if (chunks == null) {
            return;
        }
        chunks.forEach(chunk -> removeObject(chunk.getBucketName(), chunk.getObjectKey()));
    }

    /**
     * 确保 bucket 存在。
     */
    private void ensureBucket(String bucket) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    /**
     * 生成正式文件 objectKey。
     */
    private String originalObjectKey(Long userId, String fileId, String fileExt) {
        LocalDate today = LocalDate.now();
        String suffix = fileExt == null || fileExt.isBlank() ? "bin" : fileExt;
        return "users/%s/original/%d/%02d/%s.%s".formatted(userId, today.getYear(), today.getMonthValue(), fileId, suffix);
    }
}
