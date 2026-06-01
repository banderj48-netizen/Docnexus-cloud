package com.xyf.docnexus.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Gateway 本地鉴权缓存配置。
 *
 * <p>该配置控制 L1 Caffeine 缓存是否启用、缓存 TTL、最大容量和强校验路径。
 * 强校验路径会绕过本地缓存，每次都进行 JWT 验签和 Redis MGET 校验。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "docnexus.gateway-auth-cache")
public class GatewayAuthCacheProperties {

    /**
     * 是否启用 Gateway 本地鉴权缓存。
     */
    private Boolean enabled = true;

    /**
     * L1 本地缓存 TTL，单位毫秒。
     *
     * <p>TTL 越长，Redis 压力越小，但退出登录、改密码后的普通接口失效窗口也越长。
     * 当前默认 3 秒，兼顾安全和性能。</p>
     */
    private Long ttlMs = 3000L;

    /**
     * Caffeine 最大缓存条目数。
     *
     * <p>该值防止恶意请求或异常流量撑爆 Gateway 堆内存。</p>
     */
    private Long maxSize = 300000L;

    /**
     * 强校验路径。
     *
     * <p>这些路径涉及退出登录、改密码、上传删除等高风险操作，
     * 必须每次查 Redis，不能使用本地缓存。</p>
     */
    private List<String> strictPaths = new ArrayList<>();
}
