package com.xyf.docnexus.user.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyf.docnexus.common.pojo.RedisTokenSession;
import com.xyf.docnexus.common.security.AuthRedisKeys;
import com.xyf.docnexus.user.entity.UserSession;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.ThreadLocalRandom;

/**
 * heartbeat 会话归属缓存。
 *
 * <p>heartbeat 是高频接口，如果每次都查询 MySQL user_session，会导致账户中心打开后明显卡顿。
 * 该缓存保存 sessionId 对应的 userId 和 refreshToken 授权过期时间，
 * 让绝大多数 heartbeat 只访问 Redis，不再访问 MySQL。</p>
 */
@Slf4j
@Component
public class HeartbeatSessionCacheStore {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public HeartbeatSessionCacheStore(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 读取 heartbeat 会话归属缓存。
     */
    public HeartbeatSessionSnapshot get(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        try {
            String value = stringRedisTemplate.opsForValue().get(AuthRedisKeys.heartbeatSessionKey(sessionId));
            if (!StringUtils.hasText(value)) {
                return null;
            }
            HeartbeatSessionSnapshot snapshot = objectMapper.readValue(value, HeartbeatSessionSnapshot.class);
            if (!snapshot.isValid()) {
                delete(sessionId);
                return null;
            }
            return snapshot;
        } catch (Exception exception) {
            log.warn("读取 heartbeat 会话缓存失败，准备删除坏缓存，sessionId={}", sessionId, exception);
            delete(sessionId);
            return null;
        }
    }

    /**
     * 写入 heartbeat 会话归属缓存。
     *
     * <p>缓存 TTL 不超过 refreshToken 剩余有效期，也不会超过 5 分钟。
     * 这样既能减少 MySQL 查询，又不会让已经过期的授权会话长期停留在 Redis。</p>
     */
    public void save(UserSession session) {
        if (session == null || !StringUtils.hasText(session.getSessionId()) || session.getRefreshExpiresAt() == null) {
            return;
        }
        long refreshRemainMillis = session.getRefreshExpiresAt()
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli() - System.currentTimeMillis();
        if (refreshRemainMillis <= 0) {
            return;
        }
        long ttlMillis = Math.min(jitterHeartbeatTtlMillis(), refreshRemainMillis);
        HeartbeatSessionSnapshot snapshot = new HeartbeatSessionSnapshot();
        snapshot.setSessionId(session.getSessionId());
        snapshot.setUserId(session.getUserId());
        snapshot.setRefreshExpiresAtMillis(System.currentTimeMillis() + refreshRemainMillis);

        try {
            stringRedisTemplate.opsForValue().set(
                    AuthRedisKeys.heartbeatSessionKey(session.getSessionId()),
                    objectMapper.writeValueAsString(snapshot),
                    Duration.ofMillis(ttlMillis)
            );
        } catch (Exception exception) {
            log.warn("写入 heartbeat 会话缓存失败，sessionId={}", session.getSessionId(), exception);
        }
    }

    /**
     * 根据 Redis access session 写入 heartbeat 会话归属缓存。
     *
     * <p>当 `auth:heartbeat:session:{sessionId}` 因 TTL 到期丢失时，
     * heartbeat 可以从当前 accessToken 的 Redis 登录态中恢复缓存。
     * 这样刷新页面后的首次 heartbeat 不需要直接查询 MySQL。</p>
     */
    public void save(RedisTokenSession session) {
        if (session == null
                || !StringUtils.hasText(session.getSessionId())
                || session.getUserId() == null
                || session.getRefreshExpiresAtMillis() == null) {
            return;
        }
        long refreshRemainMillis = session.getRefreshExpiresAtMillis() - System.currentTimeMillis();
        if (refreshRemainMillis <= 0) {
            return;
        }
        long ttlMillis = Math.min(jitterHeartbeatTtlMillis(), refreshRemainMillis);
        HeartbeatSessionSnapshot snapshot = new HeartbeatSessionSnapshot();
        snapshot.setSessionId(session.getSessionId());
        snapshot.setUserId(session.getUserId());
        snapshot.setRefreshExpiresAtMillis(session.getRefreshExpiresAtMillis());

        try {
            stringRedisTemplate.opsForValue().set(
                    AuthRedisKeys.heartbeatSessionKey(session.getSessionId()),
                    objectMapper.writeValueAsString(snapshot),
                    Duration.ofMillis(ttlMillis)
            );
        } catch (Exception exception) {
            log.warn("根据 Redis 登录态写入 heartbeat 会话缓存失败，sessionId={}", session.getSessionId(), exception);
        }
    }

    /**
     * 删除 heartbeat 会话归属缓存。
     */
    public void delete(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        try {
            stringRedisTemplate.delete(AuthRedisKeys.heartbeatSessionKey(sessionId));
        } catch (DataAccessException exception) {
            log.warn("删除 heartbeat 会话缓存失败，sessionId={}", sessionId, exception);
        }
    }

    /**
     * 生成带小抖动的 heartbeat 归属缓存 TTL。
     *
     * <p>该缓存不是安全过期来源，可以增加 0-30 秒抖动，避免大量登录会话同时写入后又同时过期。</p>
     */
    private long jitterHeartbeatTtlMillis() {
        return CACHE_TTL.plusSeconds(ThreadLocalRandom.current().nextLong(31)).toMillis();
    }

    /**
     * heartbeat 缓存快照是否已经过期。
     */
    public boolean isExpired(HeartbeatSessionSnapshot snapshot, LocalDateTime now) {
        if (snapshot == null || snapshot.getRefreshExpiresAtMillis() == null) {
            return true;
        }
        return System.currentTimeMillis() >= snapshot.getRefreshExpiresAtMillis();
    }

    /**
     * heartbeat 会话归属缓存值。
     */
    @Data
    public static class HeartbeatSessionSnapshot {
        private String sessionId;
        private Long userId;
        private Long refreshExpiresAtMillis;

        /**
         * 判断缓存是否包含必要字段。
         */
        public boolean isValid() {
            return StringUtils.hasText(sessionId)
                    && userId != null
                    && userId > 0
                    && refreshExpiresAtMillis != null
                    && refreshExpiresAtMillis > 0;
        }
    }
}
