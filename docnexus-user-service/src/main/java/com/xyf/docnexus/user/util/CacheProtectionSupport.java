package com.xyf.docnexus.user.util;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 用户服务缓存防护工具。
 *
 * <p>该类集中提供随机 TTL、空值缓存标记和 Redis 短锁能力，
 * 用于降低缓存穿透、缓存击穿和缓存雪崩风险。</p>
 */
@Component
public class CacheProtectionSupport {

    /**
     * 空值缓存标记。
     *
     * <p>数据库查不到数据时短暂写入该值，避免恶意请求用不存在的 ID 或用户名反复打穿数据库。</p>
     */
    public static final String NULL_VALUE = "__NULL__";

    private final StringRedisTemplate stringRedisTemplate;

    public CacheProtectionSupport(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 生成带随机抖动的 TTL。
     *
     * <p>缓存雪崩常见原因是大量 Key 同一时间过期。
     * 基础 TTL 加随机秒数后，可以让过期时间自然错开。</p>
     */
    public Duration jitterSeconds(long baseSeconds, long jitterSeconds) {
        long safeBaseSeconds = Math.max(1L, baseSeconds);
        long safeJitterSeconds = Math.max(0L, jitterSeconds);
        long extraSeconds = safeJitterSeconds == 0
                ? 0
                : ThreadLocalRandom.current().nextLong(safeJitterSeconds + 1);
        return Duration.ofSeconds(safeBaseSeconds + extraSeconds);
    }

    /**
     * 生成短空值缓存 TTL。
     *
     * <p>空值缓存不能太久，否则刚创建的数据可能短时间内仍被空缓存挡住。</p>
     */
    public Duration nullValueTtl() {
        return jitterSeconds(30, 60);
    }

    /**
     * 尝试获取 Redis 互斥锁。
     *
     * <p>返回锁 token，释放锁时必须带上该 token，避免误删其他线程刚获得的新锁。</p>
     */
    public String tryLock(String lockKey, Duration lockTtl) {
        String token = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, token, lockTtl);
        return Boolean.TRUE.equals(locked) ? token : null;
    }

    /**
     * 释放 Redis 互斥锁。
     *
     * <p>这里只在锁值仍等于当前 token 时删除，避免锁超时后被其他请求重新获得时误删。</p>
     */
    public void unlock(String lockKey, String token) {
        if (token == null) {
            return;
        }
        String lua = """
                if redis.call('get', KEYS[1]) == ARGV[1] then
                    return redis.call('del', KEYS[1])
                end
                return 0
                """;
        stringRedisTemplate.execute(new DefaultRedisScript<>(lua, Long.class), List.of(lockKey), token);
    }

    /**
     * 缓存击穿等待方法。
     *
     * <p>没有拿到锁的请求短暂等待，让持锁请求有机会完成回源并写入缓存。</p>
     */
    public void shortWait() {
        try {
            Thread.sleep(50L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
