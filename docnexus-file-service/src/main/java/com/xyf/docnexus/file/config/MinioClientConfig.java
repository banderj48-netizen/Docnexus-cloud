package com.xyf.docnexus.file.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * MinIO 客户端配置。
 *
 * <p>文件服务只通过该客户端访问对象存储，不在 MySQL 中保存文件二进制。</p>
 */
@Configuration
public class MinioClientConfig {

    /**
     * 创建 MinIO 客户端。
     */
    @Bean
    public MinioClient minioClient(FileServiceProperties properties) {
        FileServiceProperties.Minio minio = properties.getMinio();
        if (!StringUtils.hasText(minio.getEndpoint())) {
            throw new IllegalStateException("请配置 MINIO_ENDPOINT");
        }
        return MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
    }
}
