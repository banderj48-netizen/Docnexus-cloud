package com.xyf.docnexus.user.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyf.docnexus.common.pojo.RedisTokenSession;
import com.xyf.docnexus.common.security.AuthRedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

import static com.xyf.docnexus.common.constant.TokenConstant.DEFAULT_TOKEN_VERSION;
import static com.xyf.docnexus.common.constant.TokenConstant.ENABLE_BLACKLIST;

@Component
// 用户服务 Redis 登录会话存储
public class RedisTokenSessionStore {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper; // 把 Java 对象和 JSON 字符串互相转换


    public RedisTokenSessionStore(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    // 将对话信息保存到 Redis当中
    public void saveSession(RedisTokenSession session) {
        long ttlMillis = session.getExpiresAtMillis() - System.currentTimeMillis();
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("登录会话过期时间无效");
        }
        try {
            String value = objectMapper.writeValueAsString(session);
            stringRedisTemplate.opsForValue().set(
                    AuthRedisKeys.sessionKey(session.getJwtId()),
                    value,
                    Duration.ofMillis(ttlMillis)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化登录会话失败", exception);
        }
    }

    /**
     * 根据 accessToken 的 jti 读取 Redis 登录态。
     *
     * <p>该方法主要用于 heartbeat 缓存自愈：网关已经校验过当前 accessToken 有效，
     * user-service 可以通过 jti 读取 `auth:session:{jti}`，拿到 sessionId 与 refreshToken 过期时间，
     * 避免 heartbeat 缓存缺失时直接回源 MySQL。</p>
     */
    public RedisTokenSession getSession(String jwtId) {
        if (jwtId == null || jwtId.isBlank()) {
            return null;
        }
        String value = stringRedisTemplate.opsForValue().get(AuthRedisKeys.sessionKey(jwtId));
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, RedisTokenSession.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("反序列化登录会话失败", exception);
        }
    }

    // 退出登录后删除会话信息，并吧当前
    public void revokeSession(String jwtId, Long expiresAtMillis) {
        long ttlMillis = ttlUntil(expiresAtMillis);
        String lua = """
                redis.call('del', KEYS[1])
                redis.call('psetex', KEYS[2], ARGV[1], ARGV[2])
                return 1
                """;
        stringRedisTemplate.execute(
                new DefaultRedisScript<>(lua, Long.class),
                List.of(AuthRedisKeys.sessionKey(jwtId), AuthRedisKeys.blacklistKey(jwtId)),
                String.valueOf(ttlMillis),
                ENABLE_BLACKLIST
        );
    }

    /**
     * 原子吊销完整会话，并写入会话级 revoked 黑名单。
     *
     * <p>主动退出、refreshToken 过期、修改密码导致会话失效时都应该调用这个方法。
     * Lua 在 Redis 内一次性完成以下动作，避免高并发下出现“accessToken 已删、
     * 但 refreshToken 还没被禁用”这类半完成状态：</p>
     *
     * <ul>
     *     <li>删除 {@code auth:session:{accessJti}}，让网关实时鉴权失败；</li>
     *     <li>写入 {@code auth:blacklist:{accessJti}}，挡住并发中的旧 accessToken；</li>
     *     <li>写入 {@code auth:session:revoked:{sessionId}}，挡住旧 refreshToken 续签；</li>
     *     <li>删除 presence 和 heartbeat 缓存，让页面展示立即变成离线/不可用；</li>
     *     <li>从 lastSeen ZSET 删除索引，避免后续定时任务重复处理。</li>
     * </ul>
     */
    public void revokeSessionWithRefreshBlock(String jwtId,
                                              Long accessExpiresAtMillis,
                                              String sessionId,
                                              Long refreshExpiresAtMillis) {
        if (!StringUtils.hasText(jwtId)) {
            return;
        }
        if (!StringUtils.hasText(sessionId)) {
            revokeSession(jwtId, accessExpiresAtMillis);
            return;
        }

        long blacklistTtlMillis = ttlUntil(accessExpiresAtMillis);
        long revokedTtlMillis = ttlUntil(refreshExpiresAtMillis);
        String lua = """
                redis.call('del', KEYS[1])
                redis.call('psetex', KEYS[2], ARGV[1], ARGV[3])
                redis.call('psetex', KEYS[3], ARGV[2], ARGV[4])
                redis.call('del', KEYS[4])
                redis.call('zrem', KEYS[5], ARGV[5])
                redis.call('del', KEYS[6])
                return 1
                """;
        stringRedisTemplate.execute(
                new DefaultRedisScript<>(lua, Long.class),
                List.of(
                        AuthRedisKeys.sessionKey(jwtId),
                        AuthRedisKeys.blacklistKey(jwtId),
                        AuthRedisKeys.sessionRevokedKey(sessionId),
                        AuthRedisKeys.sessionPresenceKey(sessionId),
                        AuthRedisKeys.sessionPresenceLastSeenKey(),
                        AuthRedisKeys.heartbeatSessionKey(sessionId)
                ),
                String.valueOf(blacklistTtlMillis),
                String.valueOf(revokedTtlMillis),
                ENABLE_BLACKLIST,
                "REVOKED",
                sessionId
        );
    }

    /**
     * 判断会话级 revoked 黑名单是否存在。
     *
     * <p>refresh 接口和登录接管都会使用该判断。只要该 Key 存在，说明该 sessionId
     * 已经被退出或安全失效，即使 MySQL 还没异步更新完成，也不能再被续签或接管复用。</p>
     */
    public boolean isSessionRevoked(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return false;
        }
        Boolean exists = stringRedisTemplate.hasKey(AuthRedisKeys.sessionRevokedKey(sessionId));
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 计算距离指定过期时间的剩余 TTL，过期或缺失时至少保留 1 毫秒。
     */
    private long ttlUntil(Long expiresAtMillis) {
        if (expiresAtMillis == null) {
            return 1L;
        }
        long ttlMillis = expiresAtMillis - System.currentTimeMillis();
        return Math.max(ttlMillis, 1L);
    }
}
