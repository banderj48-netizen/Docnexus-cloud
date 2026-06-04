package com.xyf.docnexus.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件服务配置项。
 *
 * <p>集中管理 MinIO、上传限制和缓存参数，避免把容量、桶名和 TTL 写死在业务代码里。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "docnexus.file")
public class FileServiceProperties {

    private Minio minio = new Minio();
    private Upload upload = new Upload();
    private Cache cache = new Cache();
    private OnlyOffice onlyoffice = new OnlyOffice();
    private Internal internal = new Internal();

    @Data
    public static class Minio {
        private String endpoint;
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String originalBucket = "docnexus-original-files";
        private String tempBucket = "docnexus-upload-temp";
    }

    @Data
    public static class Upload {
        private long maxSizeBytes = 200L * 1024 * 1024;
        private long multipartThresholdBytes = 5L * 1024 * 1024;
        private long chunkSizeBytes = 10L * 1024 * 1024;
        private long sessionExpireHours = 24;
    }

    @Data
    public static class Cache {
        private long libraryBaseTtlSeconds = 7200;
        private long libraryEmptyTtlSeconds = 300;
        private long libraryJitterSeconds = 900;
        private long libraryVersionTtlSeconds = 86400;
        private long fileMetaTtlSeconds = 1800;
        private long userCacheSetTtlSeconds = 172800;
        private long lockTtlSeconds = 5;
        private long lockWaitMillis = 80;
        private int lockWaitAttempts = 8;
    }

    @Data
    public static class OnlyOffice {
        private boolean enabled = true;
        private String publicUrl = "http://127.0.0.1:8090";
        private String internalUrl = "http://127.0.0.1:8090";
        private String callbackBaseUrl = "http://127.0.0.1:8088";
        private String jwtSecret = "docnexus-onlyoffice-dev-secret";
        private long sourceTokenTtlSeconds = 3600;
        private long callbackTokenTtlSeconds = 604800;
        private long configTokenTtlSeconds = 3600;
        private int callbackDownloadTimeoutSeconds = 60;
        private int forceSaveWaitSeconds = 45;
    }

    @Data
    public static class Internal {
        private String callbackToken = "change-me";
    }
}
