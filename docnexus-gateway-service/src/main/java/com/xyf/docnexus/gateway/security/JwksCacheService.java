package com.xyf.docnexus.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.xyf.docnexus.gateway.config.GatewayJwtProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gateway JWKS 多级公钥缓存。
 *
 * <p>Gateway 不持有 JWT 私钥，只从 UserService 内部 JWKS 接口拉取公钥。
 * 查询顺序为：本地快照 Map -> Caffeine kid 缓存 -> Redis 共享 JWKS JSON -> UserService JWKS 接口。
 * 多级缓存可以减少多实例 Gateway 对 UserService 的重复访问，同时保留 fallback 公钥兜底能力。</p>
 */
@Slf4j
@Component
public class JwksCacheService {

    private static final Duration IO_TIMEOUT = Duration.ofSeconds(3);

    /**
     * Gateway JWT 配置，包含 JWKS 地址、缓存 TTL 和 Redis 缓存配置。
     */
    private final GatewayJwtProperties jwtProperties;

    /**
     * 支持服务发现的 WebClient，用于访问 UserService 内部 JWKS 接口。
     */
    private final WebClient webClient;

    /**
     * Redis 响应式客户端，用于保存多实例共享的 JWKS 原始 JSON。
     */
    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * JSON 工具，用于解析 Redis 或 UserService 返回的 JWKS。
     */
    private final ObjectMapper objectMapper;

    /**
     * L0 本地快照缓存，保存当前 JVM 最近一次成功解析出的全部 kid 公钥。
     */
    private final Map<String, RSAPublicKey> keySnapshot = new ConcurrentHashMap<>();

    /**
     * L1 Caffeine 缓存，按 kid 缓存 RSA 公钥并提供本地 TTL。
     */
    private final Cache<String, RSAPublicKey> caffeineKeyCache;

    private volatile long lastRefreshMillis = 0L;
    private volatile long lastUnknownKidRefreshMillis = 0L;

    /**
     * 创建 JWKS 多级缓存服务。
     */
    public JwksCacheService(GatewayJwtProperties jwtProperties,
                            WebClient.Builder webClientBuilder,
                            ObjectProvider<ReactiveStringRedisTemplate> redisTemplateProvider,
                            ObjectMapper objectMapper) {
        this.jwtProperties = jwtProperties;
        this.webClient = webClientBuilder.build();
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.objectMapper = objectMapper;
        this.caffeineKeyCache = Caffeine.newBuilder()
                .maximumSize(Math.max(1L, jwtProperties.getJwks().getCaffeineMaxSize()))
                .expireAfterWrite(Duration.ofSeconds(Math.max(30L, jwtProperties.getJwks().getCacheTtlSeconds())))
                .build();
    }

    /**
     * 根据 kid 查询 JWT 验签公钥。
     *
     * <p>普通请求优先使用本地缓存；本地刷新窗口过期时先尝试 Redis，再访问 UserService。
     * 遇到未知 kid 时会按最小刷新间隔跳过 Redis 并访问 UserService，保证密钥轮换后可以尽快识别新 kid。</p>
     */
    public Optional<RSAPublicKey> findPublicKey(String kid) {
        if (Boolean.FALSE.equals(jwtProperties.getJwks().getEnabled()) || !StringUtils.hasText(kid)) {
            return Optional.empty();
        }

        refreshIfExpired();
        RSAPublicKey cached = findInLocalCaches(kid);
        if (cached == null && Boolean.TRUE.equals(jwtProperties.getJwks().getRefreshOnUnknownKid())
                && shouldRefreshForUnknownKid()) {
            refreshNow(true);
            cached = findInLocalCaches(kid);
        }
        return Optional.ofNullable(cached);
    }

    /**
     * 从本地快照和 Caffeine 中读取公钥。
     */
    private RSAPublicKey findInLocalCaches(String kid) {
        RSAPublicKey snapshotKey = keySnapshot.get(kid);
        if (snapshotKey != null) {
            caffeineKeyCache.put(kid, snapshotKey);
            return snapshotKey;
        }
        RSAPublicKey caffeineKey = caffeineKeyCache.getIfPresent(kid);
        if (caffeineKey != null) {
            keySnapshot.put(kid, caffeineKey);
        }
        return caffeineKey;
    }

    /**
     * 本地刷新窗口过期时刷新 JWKS。
     */
    private void refreshIfExpired() {
        long ttlMillis = Math.max(30L, jwtProperties.getJwks().getCacheTtlSeconds()) * 1000L;
        if (System.currentTimeMillis() - lastRefreshMillis > ttlMillis) {
            refreshNow(false);
        }
    }

    /**
     * 判断未知 kid 是否允许触发远程刷新。
     */
    private boolean shouldRefreshForUnknownKid() {
        long intervalMillis = Math.max(5L, jwtProperties.getJwks().getUnknownKidRefreshIntervalSeconds()) * 1000L;
        long now = System.currentTimeMillis();
        if (now - lastUnknownKidRefreshMillis < intervalMillis) {
            return false;
        }
        lastUnknownKidRefreshMillis = now;
        return true;
    }

    /**
     * 刷新 JWKS。
     *
     * @param forceRemote 是否跳过 Redis，直接访问 UserService；未知 kid 场景需要强制远程刷新。
     */
    private synchronized void refreshNow(boolean forceRemote) {
        if (!forceRemote && loadFromRedis()) {
            return;
        }
        if (!StringUtils.hasText(jwtProperties.getJwks().getUri())) {
            touchRefreshWindow();
            return;
        }
        try {
            String jwksJson = webClient.get()
                    .uri(jwtProperties.getJwks().getUri())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(IO_TIMEOUT);
            Map<String, RSAPublicKey> refreshed = parseJwks(jwksJson);
            if (!refreshed.isEmpty()) {
                replaceLocalCaches(refreshed);
                saveToRedis(jwksJson);
                touchRefreshWindow();
                log.info("刷新 JWKS 公钥缓存成功，kidCount={}", refreshed.size());
                return;
            }
            touchRefreshWindow();
            log.warn("刷新 JWKS 公钥缓存得到空结果，将继续使用本地缓存或 fallback 公钥");
        } catch (Exception exception) {
            if (forceRemote && loadFromRedis()) {
                return;
            }
            touchRefreshWindow();
            log.warn("刷新 JWKS 公钥缓存失败，将继续使用本地缓存或 fallback 公钥", exception);
        }
    }

    /**
     * 从 Redis 读取共享 JWKS JSON 并刷新本地缓存。
     */
    private boolean loadFromRedis() {
        if (Boolean.FALSE.equals(jwtProperties.getJwks().getRedisCacheEnabled())
                || redisTemplate == null
                || !StringUtils.hasText(jwtProperties.getJwks().getRedisCacheKey())) {
            return false;
        }
        try {
            String jwksJson = redisTemplate.opsForValue()
                    .get(jwtProperties.getJwks().getRedisCacheKey())
                    .block(IO_TIMEOUT);
            Map<String, RSAPublicKey> refreshed = parseJwks(jwksJson);
            if (refreshed.isEmpty()) {
                return false;
            }
            replaceLocalCaches(refreshed);
            touchRefreshWindow();
            log.debug("从 Redis 加载 JWKS 公钥缓存成功，kidCount={}", refreshed.size());
            return true;
        } catch (Exception exception) {
            log.debug("从 Redis 加载 JWKS 公钥缓存失败，将尝试远程刷新", exception);
            return false;
        }
    }

    /**
     * 将远程 JWKS 原始 JSON 保存到 Redis，供其他 Gateway 实例复用。
     */
    private void saveToRedis(String jwksJson) {
        if (Boolean.FALSE.equals(jwtProperties.getJwks().getRedisCacheEnabled())
                || redisTemplate == null
                || !StringUtils.hasText(jwksJson)
                || !StringUtils.hasText(jwtProperties.getJwks().getRedisCacheKey())) {
            return;
        }
        try {
            long ttlSeconds = Math.max(60L, jwtProperties.getJwks().getRedisCacheTtlSeconds());
            redisTemplate.opsForValue()
                    .set(jwtProperties.getJwks().getRedisCacheKey(), jwksJson, Duration.ofSeconds(ttlSeconds))
                    .block(IO_TIMEOUT);
        } catch (Exception exception) {
            log.debug("写入 Redis JWKS 缓存失败，不影响本地验签", exception);
        }
    }

    /**
     * 解析 JWKS JSON 为 kid 与 RSA 公钥的映射。
     */
    @SuppressWarnings("unchecked")
    private Map<String, RSAPublicKey> parseJwks(String jwksJson) throws Exception {
        if (!StringUtils.hasText(jwksJson)) {
            return Map.of();
        }
        Map<String, Object> body = objectMapper.readValue(jwksJson, Map.class);
        Object keysObject = body.get("keys");
        if (!(keysObject instanceof Iterable<?> keys)) {
            return Map.of();
        }
        Map<String, RSAPublicKey> refreshed = new HashMap<>();
        for (Object item : keys) {
            if (!(item instanceof Map<?, ?> key)) {
                continue;
            }
            String kid = asText(key.get("kid"));
            String n = asText(key.get("n"));
            String e = asText(key.get("e"));
            if (StringUtils.hasText(kid) && StringUtils.hasText(n) && StringUtils.hasText(e)) {
                refreshed.put(kid, toPublicKey(n, e));
            }
        }
        return refreshed;
    }

    /**
     * 替换本地快照和 Caffeine 缓存。
     */
    private void replaceLocalCaches(Map<String, RSAPublicKey> refreshed) {
        keySnapshot.clear();
        keySnapshot.putAll(refreshed);
        caffeineKeyCache.invalidateAll();
        caffeineKeyCache.putAll(refreshed);
    }

    /**
     * 更新本地刷新窗口，避免远程不可用时每个请求都重复刷新。
     */
    private void touchRefreshWindow() {
        lastRefreshMillis = System.currentTimeMillis();
    }

    /**
     * 安全转换 JWKS 字段为字符串。
     */
    private String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 将 JWK 的 n/e 参数转换为 RSA 公钥。
     */
    private RSAPublicKey toPublicKey(String n, String e) throws Exception {
        BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(n));
        BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(e));
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) keyFactory.generatePublic(new RSAPublicKeySpec(modulus, exponent));
    }
}
