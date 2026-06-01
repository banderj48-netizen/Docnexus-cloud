package com.xyf.docnexus.user.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyf.docnexus.common.VO.UserProfileResponse;
import com.xyf.docnexus.common.security.AuthRedisKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 用户管理页个人资料 Redis 缓存。
 *
 * 这个缓存只服务用户管理页：
 * 1. 进入页面时写入；
 * 2. 点击编辑时可直接读取；
 * 3. 提交修改或离开页面时删除。
 */
@Slf4j
@Component
public class UserProfileCacheStore {

    private static final Duration PROFILE_LOCK_TTL = Duration.ofSeconds(3);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheProtectionSupport cacheProtectionSupport;

    public UserProfileCacheStore(StringRedisTemplate stringRedisTemplate,
                                 ObjectMapper objectMapper,
                                 CacheProtectionSupport cacheProtectionSupport) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.cacheProtectionSupport = cacheProtectionSupport;
    }

    public UserProfileResponse get(Long userId) {
        try {
            String value = stringRedisTemplate.opsForValue().get(AuthRedisKeys.userProfileKey(userId));
            if (!StringUtils.hasText(value)) {
                return null;
            }
            if (CacheProtectionSupport.NULL_VALUE.equals(value)) {
                return null;
            }
            UserProfileResponse profile = objectMapper.readValue(value, UserProfileResponse.class);
            if (!StringUtils.hasText(profile.getRole())
                    || !StringUtils.hasText(profile.getAccountStatus())
                    || profile.getLastLoginAtMillis() == null
                    || profile.getCreateTimeMillis() == null) {
                log.info("用户资料 Redis 缓存缺少角色、账号状态、最近登录时间或注册时间字段，删除旧缓存并回源数据库，userId={}", userId);
                delete(userId);
                return null;
            }
            return profile;
        } catch (Exception exception) {
            log.warn("读取用户资料 Redis 缓存失败，准备删除坏缓存，userId={}", userId, exception);
            delete(userId);
            return null;
        }
    }

    public void save(UserProfileResponse profile) {
        if (profile == null || profile.getUserId() == null) {
            throw new IllegalArgumentException("用户资料缓存参数无效");
        }

        try {
            String value = objectMapper.writeValueAsString(profile);
            stringRedisTemplate.opsForValue().set(
                    AuthRedisKeys.userProfileKey(profile.getUserId()),
                    value,
                    cacheProtectionSupport.jitterSeconds(1800, 300)
            );
        } catch (Exception exception) {
            throw new IllegalStateException("写入用户资料 Redis 缓存失败", exception);
        }
    }

    public void delete(Long userId) {
        if (userId == null || userId <= 0) {
            return;
        }

        try {
            stringRedisTemplate.delete(AuthRedisKeys.userProfileKey(userId));
        } catch (DataAccessException exception) {
            log.warn("删除用户资料 Redis 缓存失败，userId={}", userId, exception);
        }
    }

    /**
     * 判断是否命中用户资料空值缓存。
     *
     * <p>命中空值缓存说明近期已经回源确认该用户不存在，
     * 调用方可以直接返回用户不存在，避免重复打到 MySQL。</p>
     */
    public boolean isNullCached(Long userId) {
        if (userId == null || userId <= 0) {
            return false;
        }
        try {
            String value = stringRedisTemplate.opsForValue().get(AuthRedisKeys.userProfileKey(userId));
            return CacheProtectionSupport.NULL_VALUE.equals(value);
        } catch (DataAccessException exception) {
            log.warn("读取用户资料空值缓存失败，userId={}", userId, exception);
            return false;
        }
    }

    /**
     * 写入用户资料空值缓存。
     */
    public void saveNull(Long userId) {
        if (userId == null || userId <= 0) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(
                    AuthRedisKeys.userProfileKey(userId),
                    CacheProtectionSupport.NULL_VALUE,
                    cacheProtectionSupport.nullValueTtl()
            );
        } catch (DataAccessException exception) {
            log.warn("写入用户资料空值缓存失败，userId={}", userId, exception);
        }
    }

    /**
     * 尝试获取用户资料回源锁。
     */
    public String tryLock(Long userId) {
        return cacheProtectionSupport.tryLock(AuthRedisKeys.userProfileLockKey(userId), PROFILE_LOCK_TTL);
    }

    /**
     * 释放用户资料回源锁。
     */
    public void unlock(Long userId, String token) {
        cacheProtectionSupport.unlock(AuthRedisKeys.userProfileLockKey(userId), token);
    }

    /**
     * 短暂等待持锁请求完成缓存回填。
     */
    public void shortWait() {
        cacheProtectionSupport.shortWait();
    }
}
