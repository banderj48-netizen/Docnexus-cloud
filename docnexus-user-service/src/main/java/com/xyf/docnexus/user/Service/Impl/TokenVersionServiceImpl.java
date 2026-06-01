package com.xyf.docnexus.user.Service.Impl;

import com.xyf.docnexus.common.security.AuthRedisKeys;
import com.xyf.docnexus.user.Mapper.UserMapper;
import com.xyf.docnexus.user.Service.TokenVersionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 用户令牌版本服务实现。
 *
 * <p>MySQL 是 tokenVersion 的权威数据源，Redis 只是缓存。
 * 这样即使 Redis 数据丢失，也不会把 tokenVersion 错误重置为 1。</p>
 */
@Slf4j
@Service
public class TokenVersionServiceImpl implements TokenVersionService {

    private final StringRedisTemplate stringRedisTemplate;
    private final UserMapper userMapper;
    private final com.xyf.docnexus.user.util.CacheProtectionSupport cacheProtectionSupport;

    public TokenVersionServiceImpl(StringRedisTemplate stringRedisTemplate,
                                   UserMapper userMapper,
                                   com.xyf.docnexus.user.util.CacheProtectionSupport cacheProtectionSupport) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.userMapper = userMapper;
        this.cacheProtectionSupport = cacheProtectionSupport;
    }

    /**
     * 获取当前用户真实 tokenVersion。
     *
     * <p>读取顺序：
     * 1. 优先读取 Redis；
     * 2. Redis 不存在时读取 MySQL；
     * 3. MySQL 读取成功后回写 Redis；
     * 4. Redis 值损坏时删除坏缓存，并回源 MySQL。</p>
     */
    @Override
    public Long getCurrentTokenVersion(Long userId) {
        validateUserId(userId);

        String key = AuthRedisKeys.tokenVersionKey(userId);

        try {
            String cachedValue = stringRedisTemplate.opsForValue().get(key);
            Long cachedVersion = parseRedisTokenVersion(cachedValue, key);

            if (cachedVersion != null) {
                return cachedVersion;
            }
        } catch (DataAccessException exception) {
            log.warn("读取 Redis tokenVersion 失败，降级读取 MySQL，userId={}", userId, exception);
        }

        String lockKey = AuthRedisKeys.tokenVersionLockKey(userId);
        String lockToken = null;
        try {
            lockToken = cacheProtectionSupport.tryLock(lockKey, Duration.ofSeconds(3));
            if (StringUtils.hasText(lockToken)) {
                Long databaseVersion = loadTokenVersionFromDatabase(userId);
                cacheTokenVersion(userId, databaseVersion);
                return databaseVersion;
            }

            cacheProtectionSupport.shortWait();
            try {
                String cachedValue = stringRedisTemplate.opsForValue().get(key);
                Long cachedVersion = parseRedisTokenVersion(cachedValue, key);
                if (cachedVersion != null) {
                    return cachedVersion;
                }
            } catch (DataAccessException exception) {
                log.warn("等待后读取 Redis tokenVersion 仍失败，降级读取 MySQL，userId={}", userId, exception);
            }

            Long databaseVersion = loadTokenVersionFromDatabase(userId);
            cacheTokenVersion(userId, databaseVersion);
            return databaseVersion;
        } finally {
            cacheProtectionSupport.unlock(lockKey, lockToken);
        }
    }

    /**
     * 用已经从 MySQL 读取到的 tokenVersion 补热 Redis。
     *
     * <p>登录接口已经必须查询 user_account 来校验用户名、密码、账号状态和 token_version。
     * 因此登录成功后不需要为了补写 Gateway 鉴权所需的 Redis tokenVersion 再查一次 MySQL。
     * 如果调用方传入的版本为空或非法，说明这次登录数据不完整，才回退到标准读取流程。</p>
     */
    @Override
    public Long warmTokenVersion(Long userId, Long knownTokenVersion) {
        validateUserId(userId);
        if (knownTokenVersion == null || knownTokenVersion <= 0) {
            return getCurrentTokenVersion(userId);
        }
        cacheTokenVersion(userId, knownTokenVersion);
        return knownTokenVersion;
    }

    /**
     * 递增 tokenVersion。
     *
     * <p>用于修改密码、退出全部设备、封禁账号、修改权限等场景。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long increaseTokenVersion(Long userId) {
        validateUserId(userId);

        int rows = userMapper.increaseTokenVersionById(userId);
        if (rows != 1) {
            throw new IllegalStateException("递增 tokenVersion 失败，userId=" + userId + ", rows=" + rows);
        }

        Long latestVersion = userMapper.selectTokenVersionById(userId);
        if (latestVersion == null || latestVersion <= 0) {
            throw new IllegalStateException("递增后读取 tokenVersion 失败，userId=" + userId);
        }

        cacheTokenVersion(userId, latestVersion);
        return latestVersion;
    }

    /**
     * 把 MySQL 中的 tokenVersion 回写到 Redis。
     */
    private void cacheTokenVersion(Long userId, Long tokenVersion) {
        try {
            stringRedisTemplate.opsForValue().set(
                    AuthRedisKeys.tokenVersionKey(userId),
                    String.valueOf(tokenVersion),
                    cacheProtectionSupport.jitterSeconds(3600, 300)
            );
        } catch (DataAccessException exception) {
            log.warn("回写 Redis tokenVersion 失败，userId={}, tokenVersion={}", userId, tokenVersion, exception);
        }
    }

    /**
     * 从 MySQL 读取 tokenVersion，并校验结果合法性。
     */
    private Long loadTokenVersionFromDatabase(Long userId) {
        Long databaseVersion = userMapper.selectTokenVersionById(userId);
        if (databaseVersion == null) {
            throw new IllegalStateException("用户不存在或 tokenVersion 未初始化，userId=" + userId);
        }
        if (databaseVersion <= 0) {
            throw new IllegalStateException("数据库 tokenVersion 非法，userId=" + userId + ", tokenVersion=" + databaseVersion);
        }
        return databaseVersion;
    }

    /**
     * 解析 Redis 中的 tokenVersion。
     *
     * <p>返回 null 表示缓存不存在。
     * 如果缓存值格式错误，删除坏缓存，然后回源 MySQL。</p>
     */
    private Long parseRedisTokenVersion(String value, String key) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            long version = Long.parseLong(value);
            if (version <= 0) {
                throw new NumberFormatException("tokenVersion 必须大于 0");
            }
            return version;
        } catch (NumberFormatException exception) {
            log.warn("Redis tokenVersion 缓存值非法，准备删除坏缓存，key={}, value={}", key, value);
            stringRedisTemplate.delete(key);
            return null;
        }
    }

    /**
     * 校验 userId 是否合法。
     */
    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId 非法：" + userId);
        }
    }
}
