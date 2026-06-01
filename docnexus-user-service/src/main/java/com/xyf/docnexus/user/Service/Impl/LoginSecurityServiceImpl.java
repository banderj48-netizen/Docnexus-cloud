package com.xyf.docnexus.user.Service.Impl;

import com.xyf.docnexus.common.constant.ResponseCode;
import com.xyf.docnexus.common.exception.BusinessException;
import com.xyf.docnexus.common.security.AuthRedisKeys;
import com.xyf.docnexus.user.Service.LoginSecurityService;
import com.xyf.docnexus.user.config.LoginSecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

/**
 * Redis 登录安全服务实现。
 *
 * <p>设计目标：
 * 1. IP 维度限流：挡住单个来源短时间内大量请求；
 * 2. 用户名维度错误次数限制：挡住针对单个账号的暴力破解；
 * 3. Redis 异常时降级放行：避免 Redis 短暂异常导致全站无法登录，但会记录告警日志。</p>
 */
@Slf4j
@Service
public class LoginSecurityServiceImpl implements LoginSecurityService {

    private final StringRedisTemplate stringRedisTemplate;
    private final LoginSecurityProperties properties;

    public LoginSecurityServiceImpl(StringRedisTemplate stringRedisTemplate,
                                    LoginSecurityProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    @Override
    public void checkLoginAllowed(String username, String clientIp) {
        checkIpLimit(clientIp);
        checkUsernameLock(username);
    }

    @Override
    public void recordLoginFailure(String username) {
        String normalizedUsername = normalizeUsername(username);
        if (!StringUtils.hasText(normalizedUsername)) {
            return;
        }

        String failKey = AuthRedisKeys.loginFailKey(normalizedUsername);
        String lockKey = AuthRedisKeys.loginLockKey(normalizedUsername);

        try {
            Long failures = incrementWithExpire(
                    failKey,
                    Duration.ofSeconds(properties.getUsernameFailureWindowSeconds())
            );

            if (failures != null && failures >= properties.getUsernameMaxFailures()) {
                stringRedisTemplate.opsForValue().set(
                        lockKey,
                        "1",
                        Duration.ofSeconds(properties.getUsernameLockSeconds())
                );
                log.warn("用户登录失败次数达到上限，账号临时锁定，username={}, failures={}",
                        normalizedUsername, failures);
            }
        } catch (DataAccessException exception) {
            log.warn("记录登录失败次数时 Redis 异常，username={}", normalizedUsername, exception);
        }
    }

    @Override
    public void recordLoginSuccess(String username) {
        String normalizedUsername = normalizeUsername(username);
        if (!StringUtils.hasText(normalizedUsername)) {
            return;
        }

        try {
            stringRedisTemplate.delete(List.of(
                    AuthRedisKeys.loginFailKey(normalizedUsername),
                    AuthRedisKeys.loginLockKey(normalizedUsername)
            ));
        } catch (DataAccessException exception) {
            log.warn("清理登录失败次数时 Redis 异常，username={}", normalizedUsername, exception);
        }
    }

    /**
     * 检查 IP 维度登录频率。
     */
    private void checkIpLimit(String clientIp) {
        String normalizedIp = normalizeIp(clientIp);
        String key = AuthRedisKeys.loginIpLimitKey(normalizedIp);

        try {
            Long attempts = incrementWithExpire(key, Duration.ofSeconds(properties.getIpWindowSeconds()));
            if (attempts != null && attempts > properties.getIpMaxAttempts()) {
                log.warn("登录请求触发 IP 限流，clientIp={}, attempts={}", normalizedIp, attempts);
                throw new BusinessException(ResponseCode.TOO_MANY_REQUESTS, "登录请求过于频繁，请稍后再试");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            log.warn("检查登录 IP 限流时 Redis 异常，clientIp={}", normalizedIp, exception);
        }
    }

    /**
     * 检查用户名是否处于临时锁定状态。
     */
    private void checkUsernameLock(String username) {
        String normalizedUsername = normalizeUsername(username);
        if (!StringUtils.hasText(normalizedUsername)) {
            return;
        }

        String lockKey = AuthRedisKeys.loginLockKey(normalizedUsername);
        try {
            Boolean locked = stringRedisTemplate.hasKey(lockKey);
            if (Boolean.TRUE.equals(locked)) {
                Long ttl = stringRedisTemplate.getExpire(lockKey);
                long waitSeconds = ttl == null || ttl < 0 ? properties.getUsernameLockSeconds() : ttl;
                throw new BusinessException(
                        ResponseCode.TOO_MANY_REQUESTS,
                        "密码错误次数过多，账号已临时锁定，请 " + waitSeconds + " 秒后再试"
                );
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            log.warn("检查账号登录锁定状态时 Redis 异常，username={}", normalizedUsername, exception);
        }
    }

    /**
     * Redis INCR + 首次设置过期时间。
     *
     * <p>INCR 是 Redis 原子操作，可以在高并发登录时安全累加计数。</p>
     */
    private Long incrementWithExpire(String key, Duration ttl) {
        String lua = """
                local count = redis.call('incr', KEYS[1])
                if count == 1 then
                    redis.call('pexpire', KEYS[1], ARGV[1])
                end
                return count
                """;
        return stringRedisTemplate.execute(
                new DefaultRedisScript<>(lua, Long.class),
                List.of(key),
                String.valueOf(ttl.toMillis())
        );
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private String normalizeIp(String clientIp) {
        return StringUtils.hasText(clientIp) ? clientIp.trim() : "0.0.0.0";
    }
}
