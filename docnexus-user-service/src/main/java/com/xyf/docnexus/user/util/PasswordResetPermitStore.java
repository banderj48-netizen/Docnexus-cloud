package com.xyf.docnexus.user.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xyf.docnexus.common.security.AuthRedisKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 找回密码 resetToken 的 Redis 存储。
 *
 * <p>用于替代 UserServiceImpl 中的 ConcurrentHashMap，
 * 支持多实例 user-service 共享 resetToken。</p>
 */
@Component
public class PasswordResetPermitStore {
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public PasswordResetPermitStore(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存重置密码许可。
     *
     * @param username 用户名
     * @param userId 用户 ID
     * @param resetToken 重置密码令牌
     * @param ttlMillis 有效期，毫秒
     */
    public void save(String username, Long userId, String resetToken, long ttlMillis) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("username 不能为空");
        }
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId 非法：" + userId);
        }
        if (!StringUtils.hasText(resetToken)) {
            throw new IllegalArgumentException("resetToken 不能为空");
        }
        if (ttlMillis <= 0) {
            throw new IllegalArgumentException("resetToken 有效期非法：" + ttlMillis);
        }

        long expireAtMillis = System.currentTimeMillis() + ttlMillis;
        ResetPermit permit = new ResetPermit(userId, resetToken, expireAtMillis);

        try {
            String value = objectMapper.writeValueAsString(permit);
            stringRedisTemplate.opsForValue().set(
                    AuthRedisKeys.passwordResetPermitKey(username),
                    value,
                    Duration.ofMillis(ttlMillis)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化重置密码许可失败", exception);
        }
    }

    /**
     * 获取重置密码许可。
     */
    public ResetPermit get(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }

        String value = stringRedisTemplate.opsForValue().get(
                AuthRedisKeys.passwordResetPermitKey(username)
        );

        if (!StringUtils.hasText(value)) {
            return null;
        }

        try {
            return objectMapper.readValue(value, ResetPermit.class);
        } catch (JsonProcessingException exception) {
            stringRedisTemplate.delete(AuthRedisKeys.passwordResetPermitKey(username));
            throw new IllegalStateException("反序列化重置密码许可失败，已删除坏缓存", exception);
        }
    }

    /**
     * 删除重置密码许可。
     */
    public void delete(String username) {
        if (!StringUtils.hasText(username)) {
            return;
        }

        stringRedisTemplate.delete(AuthRedisKeys.passwordResetPermitKey(username));
    }

    /**
     * Redis 中保存的重置密码许可。
     */
    public record ResetPermit(
            Long userId,
            String resetToken,
            Long expireAtMillis
    ) {
    }
}
