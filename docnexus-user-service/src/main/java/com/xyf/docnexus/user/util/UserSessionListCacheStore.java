package com.xyf.docnexus.user.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyf.docnexus.common.security.AuthRedisKeys;
import com.xyf.docnexus.user.entity.UserSession;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 用户会话列表 Redis 缓存。
 *
 * <p>账户中心每次刷新都会展示会话列表，如果每次都执行 count + page 两条 SQL，
 * 在远程 MySQL 或高并发场景下会明显拖慢页面。该缓存只保存当前页的 user_session 原始记录，
 * online 字段仍在返回前从 Redis presence 实时计算，避免在线状态完全变成旧数据。</p>
 */
@Slf4j
@Component
public class UserSessionListCacheStore {

    private static final Duration BASE_TTL = Duration.ofMinutes(30);
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public UserSessionListCacheStore(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 读取用户会话列表分页缓存。
     */
    public CacheValue get(Long userId, Integer pageNum, Integer pageSize) {
        return get(userId, currentVersion(userId), pageNum, pageSize);
    }

    /**
     * 按指定版本读取用户会话列表分页缓存。
     *
     * <p>会话列表使用版本号隔离并发失效。业务线程先读取当前版本，再查询缓存和数据库；
     * 如果查询数据库期间发生登录、退出或会话失效，版本号会递增，旧线程后续只会把旧结果写入旧版本 Key，
     * 新请求会读取新版本 Key，不会被旧数据污染。</p>
     */
    public CacheValue get(Long userId, String version, Integer pageNum, Integer pageSize) {
        if (userId == null || userId <= 0 || pageNum == null || pageSize == null) {
            return null;
        }
        try {
            String key = AuthRedisKeys.userSessionListPageKey(userId, version, pageNum, pageSize);
            String value = stringRedisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(value)) {
                return null;
            }
            CacheValue cacheValue = objectMapper.readValue(value, CacheValue.class);
            return cacheValue == null || !cacheValue.isValid() ? null : cacheValue;
        } catch (Exception exception) {
            log.warn("读取用户会话列表缓存失败，userId={}, pageNum={}, pageSize={}",
                    userId, pageNum, pageSize, exception);
            return null;
        }
    }

    /**
     * 写入用户会话列表分页缓存。
     */
    public void save(Long userId, Integer pageNum, Integer pageSize, Long total, List<UserSession> records) {
        save(userId, currentVersion(userId), pageNum, pageSize, total, records);
    }

    /**
     * 按指定版本写入用户会话列表分页缓存。
     *
     * <p>这里不重新读取版本号，避免数据库查询期间版本变化后，把旧查询结果写到新版本缓存里。</p>
     */
    public void save(Long userId, String version, Integer pageNum, Integer pageSize, Long total, List<UserSession> records) {
        if (userId == null || userId <= 0 || pageNum == null || pageSize == null || records == null) {
            return;
        }
        try {
            String key = AuthRedisKeys.userSessionListPageKey(userId, version, pageNum, pageSize);
            CacheValue value = new CacheValue();
            value.setTotal(total == null ? 0L : total);
            value.setPageNum(pageNum);
            value.setPageSize(pageSize);
            value.setRecords(records);

            long jitterSeconds = ThreadLocalRandom.current().nextLong(0, 301);
            stringRedisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(value),
                    BASE_TTL.plusSeconds(jitterSeconds)
            );
        } catch (JsonProcessingException exception) {
            log.warn("写入用户会话列表缓存失败，userId={}, pageNum={}, pageSize={}",
                    userId, pageNum, pageSize, exception);
        }
    }

    /**
     * 递增用户会话列表缓存版本。
     *
     * <p>不直接删除分页缓存，避免 scan 或批量 delete。旧版本缓存会在短 TTL 后自然过期。</p>
     */
    public void invalidate(Long userId) {
        if (userId == null || userId <= 0) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().increment(AuthRedisKeys.userSessionListVersionKey(userId));
        } catch (Exception exception) {
            log.warn("递增用户会话列表缓存版本失败，userId={}", userId, exception);
        }
    }

    /**
     * 获取当前缓存版本号。
     */
    public String currentVersion(Long userId) {
        if (userId == null || userId <= 0) {
            return "0";
        }
        try {
            String version = stringRedisTemplate.opsForValue().get(AuthRedisKeys.userSessionListVersionKey(userId));
            return StringUtils.hasText(version) ? version : "0";
        } catch (Exception exception) {
            log.warn("读取用户会话列表缓存版本失败，降级为数据库查询，userId={}", userId, exception);
            return "0";
        }
    }

    /**
     * 用户会话列表分页缓存值。
     */
    @Data
    public static class CacheValue {
        private Long total;
        private Integer pageNum;
        private Integer pageSize;
        private List<UserSession> records;

        /**
         * 判断缓存内容是否可用。
         */
        public boolean isValid() {
            return total != null
                    && pageNum != null
                    && pageSize != null
                    && records != null;
        }
    }
}
