package com.xyf.docnexus.user.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyf.docnexus.common.DTO.UserDTO;
import com.xyf.docnexus.common.security.AuthRedisKeys;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 登录用户快照缓存。
 *
 * <p>该缓存用于登录接口快速读取用户基础信息，减少每次登录都访问 MySQL 用户表。
 * 缓存中包含密码字段，当前项目密码仍为 Base64 存储；后续升级 BCrypt 后这里应缓存密码哈希。</p>
 */
@Slf4j
@Component
public class LoginUserCacheStore {

    private static final Duration LOCK_TTL = Duration.ofSeconds(3);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheProtectionSupport cacheProtectionSupport;

    public LoginUserCacheStore(StringRedisTemplate stringRedisTemplate,
                               ObjectMapper objectMapper,
                               CacheProtectionSupport cacheProtectionSupport) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.cacheProtectionSupport = cacheProtectionSupport;
    }

    /**
     * 读取登录用户快照缓存。
     *
     * <p>返回对象中的 hit 表示 Redis 已经命中；
     * nullValue 表示命中空值缓存，调用方不需要再查 MySQL。</p>
     */
    public CacheResult get(String username) {
        String normalizedUsername = normalizeUsername(username);
        if (!StringUtils.hasText(normalizedUsername)) {
            return CacheResult.miss();
        }

        try {
            String value = stringRedisTemplate.opsForValue().get(AuthRedisKeys.userLoginKey(normalizedUsername));
            if (!StringUtils.hasText(value)) {
                return CacheResult.miss();
            }
            if (CacheProtectionSupport.NULL_VALUE.equals(value)) {
                return CacheResult.nullHit();
            }
            LoginUserSnapshot snapshot = objectMapper.readValue(value, LoginUserSnapshot.class);
            if (!snapshot.isValid()) {
                delete(normalizedUsername);
                return CacheResult.miss();
            }
            return CacheResult.hit(snapshot.toUserDTO());
        } catch (Exception exception) {
            log.warn("读取登录用户快照缓存失败，准备删除坏缓存，username={}", normalizedUsername, exception);
            delete(normalizedUsername);
            return CacheResult.miss();
        }
    }

    /**
     * 写入登录用户快照缓存。
     */
    public void save(UserDTO user) {
        if (user == null || !StringUtils.hasText(user.getUsername())) {
            return;
        }
        String normalizedUsername = normalizeUsername(user.getUsername());
        try {
            String value = objectMapper.writeValueAsString(LoginUserSnapshot.from(user));
            stringRedisTemplate.opsForValue().set(
                    AuthRedisKeys.userLoginKey(normalizedUsername),
                    value,
                    cacheProtectionSupport.jitterSeconds(600, 120)
            );
        } catch (Exception exception) {
            log.warn("写入登录用户快照缓存失败，username={}", normalizedUsername, exception);
        }
    }

    /**
     * 写入用户名不存在的空值缓存。
     */
    public void saveNull(String username) {
        String normalizedUsername = normalizeUsername(username);
        if (!StringUtils.hasText(normalizedUsername)) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(
                    AuthRedisKeys.userLoginKey(normalizedUsername),
                    CacheProtectionSupport.NULL_VALUE,
                    cacheProtectionSupport.nullValueTtl()
            );
        } catch (DataAccessException exception) {
            log.warn("写入登录用户空值缓存失败，username={}", normalizedUsername, exception);
        }
    }

    /**
     * 删除登录用户快照缓存。
     */
    public void delete(String username) {
        String normalizedUsername = normalizeUsername(username);
        if (!StringUtils.hasText(normalizedUsername)) {
            return;
        }
        try {
            stringRedisTemplate.delete(AuthRedisKeys.userLoginKey(normalizedUsername));
        } catch (DataAccessException exception) {
            log.warn("删除登录用户快照缓存失败，username={}", normalizedUsername, exception);
        }
    }

    /**
     * 尝试获取登录用户快照回源锁。
     */
    public String tryLock(String username) {
        return cacheProtectionSupport.tryLock(AuthRedisKeys.userLoginLockKey(normalizeUsername(username)), LOCK_TTL);
    }

    /**
     * 释放登录用户快照回源锁。
     */
    public void unlock(String username, String token) {
        cacheProtectionSupport.unlock(AuthRedisKeys.userLoginLockKey(normalizeUsername(username)), token);
    }

    /**
     * 短暂等待持锁请求完成缓存回填。
     */
    public void shortWait() {
        cacheProtectionSupport.shortWait();
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    /**
     * 登录用户缓存读取结果。
     */
    public record CacheResult(boolean hit, boolean nullValue, UserDTO user) {
        public static CacheResult miss() {
            return new CacheResult(false, false, null);
        }

        public static CacheResult nullHit() {
            return new CacheResult(true, true, null);
        }

        public static CacheResult hit(UserDTO user) {
            return new CacheResult(true, false, user);
        }
    }

    /**
     * 登录用户快照缓存值。
     */
    @Data
    public static class LoginUserSnapshot {
        private Long id;
        private String username;
        private String password;
        private String role;
        private String status;
        private Long tokenVersion;
        private LocalDateTime createTime;

        /**
         * 从数据库 DTO 构造缓存快照。
         */
        public static LoginUserSnapshot from(UserDTO user) {
            LoginUserSnapshot snapshot = new LoginUserSnapshot();
            snapshot.setId(user.getId());
            snapshot.setUsername(user.getUsername());
            snapshot.setPassword(user.getPassword());
            snapshot.setRole(user.getRole());
            snapshot.setStatus(user.getStatus());
            snapshot.setTokenVersion(user.getTokenVersion());
            snapshot.setCreateTime(user.getCreateTime());
            return snapshot;
        }

        /**
         * 转换为业务层使用的 UserDTO。
         */
        public UserDTO toUserDTO() {
            UserDTO user = new UserDTO();
            user.setId(id);
            user.setUsername(username);
            user.setPassword(password);
            user.setRole(role);
            user.setStatus(status);
            user.setTokenVersion(tokenVersion);
            user.setCreateTime(createTime);
            return user;
        }

        /**
         * 判断缓存快照是否包含登录所需字段。
         */
        public boolean isValid() {
            return id != null
                    && StringUtils.hasText(username)
                    && StringUtils.hasText(password)
                    && StringUtils.hasText(role)
                    && StringUtils.hasText(status)
                    && tokenVersion != null
                    && tokenVersion > 0;
        }
    }
}
