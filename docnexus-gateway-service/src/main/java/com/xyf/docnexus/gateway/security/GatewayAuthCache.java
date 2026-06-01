package com.xyf.docnexus.gateway.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.xyf.docnexus.gateway.config.GatewayAuthCacheProperties;
import com.xyf.docnexus.gateway.util.JwtVerifyTool.UserTokenPayload;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Gateway L1 本地鉴权缓存。
 *
 * <p>该缓存只保存已经通过 JWT 验签和 Redis 校验的 token 身份。
 * 缓存 key 使用 accessToken + clientIp 的 SHA-256，避免 IP 绑定后同一个 token
 * 在不同 IP 下命中本地缓存。</p>
 */
@Component
public class GatewayAuthCache {

    private final GatewayAuthCacheProperties properties;
    private final Cache<String, UserTokenPayload> cache;

    public GatewayAuthCache(GatewayAuthCacheProperties properties) {
        this.properties = properties;
        long safeMaxSize = properties.getMaxSize() == null || properties.getMaxSize() <= 0
                ? 300000L
                : properties.getMaxSize();
        long safeTtlMs = properties.getTtlMs() == null || properties.getTtlMs() <= 0
                ? 3000L
                : properties.getTtlMs();
        this.cache = Caffeine.newBuilder()
                .maximumSize(safeMaxSize)
                .expireAfterWrite(Duration.ofMillis(safeTtlMs))
                .recordStats()
                .build();
    }

    /**
     * 读取仍然有效的本地鉴权缓存。
     */
    public UserTokenPayload getIfValid(String cacheKey) {
        if (!isEnabled() || cacheKey == null || cacheKey.isBlank()) {
            return null;
        }
        UserTokenPayload payload = cache.getIfPresent(cacheKey);
        if (payload == null) {
            return null;
        }
        if (payload.expiresAtMillis() == null || System.currentTimeMillis() >= payload.expiresAtMillis()) {
            cache.invalidate(cacheKey);
            return null;
        }
        return payload;
    }

    /**
     * 写入本地鉴权缓存。
     */
    public void put(String cacheKey, UserTokenPayload payload) {
        if (!isEnabled()
                || cacheKey == null
                || cacheKey.isBlank()
                || payload == null
                || payload.expiresAtMillis() == null
                || System.currentTimeMillis() >= payload.expiresAtMillis()) {
            return;
        }
        cache.put(cacheKey, payload);
    }

    /**
     * 计算 accessToken + clientIp 的 SHA-256 缓存 key。
     */
    public String hashToken(String token, String clientIp) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token 不能为空");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String rawKey = token + "|" + (clientIp == null ? "" : clientIp);
            byte[] bytes = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception exception) {
            throw new IllegalStateException("计算 token 缓存 key 失败", exception);
        }
    }

    /**
     * 判断 L1 本地缓存是否启用。
     */
    private boolean isEnabled() {
        return Boolean.TRUE.equals(properties.getEnabled());
    }
}
