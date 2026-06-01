package com.xyf.docnexus.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyf.docnexus.common.pojo.RedisTokenSession;
import com.xyf.docnexus.common.security.AuthRedisKeys;
import com.xyf.docnexus.gateway.util.JwtVerifyTool;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Redis 网关鉴权状态读取器。
 *
 * <p>Redis 是 Gateway 的 L2 实时鉴权状态源。当前实现使用 MGET 一次读取
 * blacklist、access session 和 tokenVersion，减少每个请求的 Redis 网络往返。</p>
 */
@Component
public class ReactiveRedisTokenSessionStore {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ReactiveRedisTokenSessionStore(ReactiveStringRedisTemplate redisTemplate,
                                          ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 验证 JWT 是否仍然处于 Redis 有效登录态。
     *
     * <p>除 blacklist、session 和 tokenVersion 外，这里还会校验 Redis session 中绑定的 IP。
     * Redis 异常或解析异常时 fail closed，直接视为无效。</p>
     */
    public Mono<Boolean> isTokenActive(JwtVerifyTool.UserTokenPayload payload, String clientIp) {
        if (payload.expiresAtMillis() == null || System.currentTimeMillis() >= payload.expiresAtMillis()) {
            return Mono.just(false);
        }

        List<String> keys = List.of(
                AuthRedisKeys.blacklistKey(payload.jwtId()),
                AuthRedisKeys.sessionKey(payload.jwtId()),
                AuthRedisKeys.tokenVersionKey(payload.userId())
        );

        return redisTemplate.opsForValue()
                .multiGet(keys)
                .map(values -> isActiveByMgetResult(values, payload, clientIp))
                .defaultIfEmpty(false)
                .onErrorReturn(false);
    }

    /**
     * 根据 Redis MGET 结果判断 token 是否有效。
     */
    private boolean isActiveByMgetResult(List<String> values,
                                         JwtVerifyTool.UserTokenPayload payload,
                                         String clientIp) {
        if (values == null || values.size() != 3) {
            return false;
        }
        String blacklistValue = values.get(0);
        String sessionValue = values.get(1);
        String tokenVersionValue = values.get(2);

        if (StringUtils.hasText(blacklistValue)) {
            return false;
        }
        if (!StringUtils.hasText(sessionValue)) {
            return false;
        }
        if (!StringUtils.hasText(tokenVersionValue)) {
            return false;
        }

        try {
            RedisTokenSession session = objectMapper.readValue(sessionValue, RedisTokenSession.class);
            Long redisTokenVersion = Long.valueOf(tokenVersionValue);
            if (!redisTokenVersion.equals(payload.tokenVersion())) {
                return false;
            }
            if (!payload.jwtId().equals(session.getJwtId())) {
                return false;
            }
            if (session.getUserId() == null || !session.getUserId().equals(payload.userId())) {
                return false;
            }
            if (!StringUtils.hasText(session.getBoundIp()) || !StringUtils.hasText(clientIp)) {
                return false;
            }
            return session.getBoundIp().equals(clientIp);
        } catch (Exception exception) {
            return false;
        }
    }
}
