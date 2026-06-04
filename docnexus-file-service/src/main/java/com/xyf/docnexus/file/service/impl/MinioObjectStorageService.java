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

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.List;

/**
 * MinIO 对象存储服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioObjectStorageService implements ObjectStorageService {

    private static final long MIN_SERVER_COMPOSE_PART_SIZE = 5L * 1024 * 1024;

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
     * 上传在线编辑生成的新正式文件版本。
     */
    @Override
    public StoredObject uploadEditorVersion(Long userId, String fileId, String fileExt, int versionNumber, byte[] content, String contentType) {
        String bucket = properties.getMinio().getOriginalBucket();
        String objectKey = editorVersionObjectKey(userId, fileId, fileExt, versionNumber);
        byte[] bytes = content == null ? new byte[0] : content;
        try {
            ensureBucket(bucket);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(new java.io.ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType(contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType)
                    .build());
            return new StoredObject(bucket, objectKey);
        } catch (Exception exception) {
            throw new IllegalStateException("上传编辑版本到 MinIO 失败", exception);
        }
    }

    /**
     * 流式上传在线编辑版本，避免 OnlyOffice 回调保存大文件时整文件进入 JVM 内存。
     */
    @Override
    public StoredObject uploadEditorVersion(Long userId, String fileId, String fileExt, int versionNumber, InputStream inputStream, long size, String contentType) {
        String bucket = properties.getMinio().getOriginalBucket();
        String objectKey = editorVersionObjectKey(userId, fileId, fileExt, versionNumber);
        try (InputStream stream = inputStream) {
            ensureBucket(bucket);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(stream, size, -1)
                    .contentType(contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType)
                    .build());
            return new StoredObject(bucket, objectKey);
        } catch (Exception exception) {
            throw new IllegalStateException("上传编辑版本到 MinIO 失败", exception);
        }
    }

    /**
     * 覆盖写入已有正式对象。
     */
    @Override
    public StoredObject overwriteObject(String bucketName, String objectKey, byte[] content, String contentType) {
        byte[] bytes = content == null ? new byte[0] : content;
        try {
            ensureBucket(bucketName);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .stream(new java.io.ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType(contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType)
                    .build());
            return new StoredObject(bucketName, objectKey);
        } catch (Exception exception) {
            throw new IllegalStateException("覆盖写入 MinIO 文件失败", exception);
        }
    }

    /**
     * 流式覆盖写入已有正式对象，避免大文件保存时整文件进入 JVM 内存。
     */
    @Override
    public StoredObject overwriteObject(String bucketName, String objectKey, InputStream inputStream, long size, String contentType) {
        try (InputStream stream = inputStream) {
            ensureBucket(bucketName);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .stream(stream, size, -1)
                    .contentType(contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType)
                    .build());
            return new StoredObject(bucketName, objectKey);
        } catch (Exception exception) {
            throw new IllegalStateException("覆盖写入 MinIO 文件失败", exception);
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
            if (!canUseServerCompose(chunks)) {
                return uploadMergedStream(bucket, objectKey, chunks);
            }
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
     * 判断是否可以使用 MinIO 服务端合并。
     *
     * <p>S3/MinIO multipart compose 对非最后分片通常要求不小于 5MiB；
     * 不满足时改用 FileService 流式拼接上传，避免历史小分片会话在 99% 合并阶段失败。</p>
     */
    private boolean canUseServerCompose(List<FileUploadChunk> chunks) {
        if (chunks.size() <= 1) {
            return false;
        }
        for (int index = 0; index < chunks.size() - 1; index++) {
            Long chunkSize = chunks.get(index).getChunkSize();
            if (chunkSize == null || chunkSize < MIN_SERVER_COMPOSE_PART_SIZE) {
                return false;
            }
        }
        return true;
    }

    /**
     * 流式拼接临时分片并上传为正式对象，不把完整文件读入 JVM 内存。
     */
    private StoredObject uploadMergedStream(String bucket, String objectKey, List<FileUploadChunk> chunks) {
        long totalSize = chunks.stream()
                .map(FileUploadChunk::getChunkSize)
                .filter(size -> size != null && size > 0)
                .mapToLong(Long::longValue)
                .sum();
        try (InputStream mergedStream = new MinioChunkSequenceInputStream(chunks)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(mergedStream, totalSize, -1)
                    .contentType("application/octet-stream")
                    .build());
            return new StoredObject(bucket, objectKey);
        } catch (Exception exception) {
            throw new IllegalStateException("流式合并 MinIO 分片失败", exception);
        }
    }

    /**
     * 按分片顺序懒加载 MinIO 临时对象的输入流。
     */
    private class MinioChunkSequenceInputStream extends InputStream {
        private final Iterator<FileUploadChunk> iterator;
        private InputStream currentStream;
        private boolean closed;

        private MinioChunkSequenceInputStream(List<FileUploadChunk> chunks) {
            this.iterator = chunks.iterator();
        }

        /**
         * 读取一个字节。
         */
        @Override
        public int read() throws IOException {
            byte[] buffer = new byte[1];
            int read = read(buffer, 0, 1);
            return read == -1 ? -1 : buffer[0] & 0xff;
        }

        /**
         * 读取字节数组，当前分片读完后自动切到下一片。
         */
        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (closed) {
                return -1;
            }
            while (true) {
                if (currentStream == null && !openNextStream()) {
                    return -1;
                }
                int read = currentStream.read(bytes, offset, length);
                if (read != -1) {
                    return read;
                }
                closeCurrentStream();
            }
        }

        /**
         * 关闭当前分片流。
         */
        @Override
        public void close() throws IOException {
            closed = true;
            closeCurrentStream();
        }

        /**
         * 打开下一个临时分片流。
         */
        private boolean openNextStream() throws IOException {
            if (!iterator.hasNext()) {
                return false;
            }
            FileUploadChunk chunk = iterator.next();
            try {
                currentStream = minioClient.getObject(GetObjectArgs.builder()
                        .bucket(chunk.getBucketName())
                        .object(chunk.getObjectKey())
                        .build());
                return true;
            } catch (Exception exception) {
                throw new IOException("读取 MinIO 临时分片失败", exception);
            }
        }

        /**
         * 安全关闭当前分片流。
         */
        private void closeCurrentStream() throws IOException {
            if (currentStream != null) {
                currentStream.close();
                currentStream = null;
            }
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

    /**
     * 生成在线编辑保存版本 objectKey。
     */
    private String editorVersionObjectKey(Long userId, String fileId, String fileExt, int versionNumber) {
        LocalDate today = LocalDate.now();
        String suffix = fileExt == null || fileExt.isBlank() ? "html" : fileExt;
        return "users/%s/editor/%d/%02d/%s/v%d.%s".formatted(userId, today.getYear(), today.getMonthValue(), fileId, versionNumber, suffix);
    }
}
