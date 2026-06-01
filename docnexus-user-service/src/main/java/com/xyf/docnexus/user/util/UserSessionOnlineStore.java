package com.xyf.docnexus.user.util;

import com.xyf.docnexus.common.security.AuthRedisKeys;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 用户会话在线状态 Redis 存储。
 *
 * <p>在线状态只代表浏览器最近发送过 heartbeat，不代表 refreshToken 授权一定有效。
 * 授权有效性仍由 Redis access session、blacklist 和 tokenVersion 判断。</p>
 */
@Component
public class UserSessionOnlineStore {

    /**
     * 在线状态 TTL。
     *
     * <p>前端当前每 10 秒发送一次 heartbeat，因此这里保持 35 秒窗口。
     * 浏览器关闭、断网或进程终止后，presence Key 会自然过期。</p>
     */
    private static final Duration PRESENCE_TTL = Duration.ofSeconds(35);
    private static final String ONLINE = "ONLINE";
    private static final String OFFLINE = "OFFLINE";

    private final StringRedisTemplate stringRedisTemplate;

    public UserSessionOnlineStore(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 处理 heartbeat，并只在状态变化时返回 true。
     *
     * <p>Lua 会原子刷新 presence TTL 和 lastSeen ZSET。
     * 如果会话已经是 ONLINE，只刷新时间，不重复触发业务状态变更。</p>
     */
    public boolean markOnline(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return false;
        }
        String lua = """
                local current = redis.call('get', KEYS[1])
                redis.call('zadd', KEYS[2], ARGV[1], ARGV[2])
                if current == ARGV[3] then
                    redis.call('pexpire', KEYS[1], ARGV[4])
                    return 0
                end
                redis.call('set', KEYS[1], ARGV[3], 'PX', ARGV[4])
                return 1
                """;
        Long changed = stringRedisTemplate.execute(
                new DefaultRedisScript<>(lua, Long.class),
                List.of(AuthRedisKeys.sessionPresenceKey(sessionId), AuthRedisKeys.sessionPresenceLastSeenKey()),
                String.valueOf(System.currentTimeMillis()),
                sessionId,
                ONLINE,
                String.valueOf(PRESENCE_TTL.toMillis())
        );
        return changed != null && changed == 1L;
    }

    /**
     * 判断某个 session 是否仍处于在线窗口内。
     */
    public boolean isOnline(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return false;
        }
        return ONLINE.equals(stringRedisTemplate.opsForValue().get(AuthRedisKeys.sessionPresenceKey(sessionId)));
    }

    /**
     * 读取 Redis ZSET 中记录的最后活跃毫秒时间戳。
     */
    public Long getLastSeenMillis(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        Double score = stringRedisTemplate.opsForZSet()
                .score(AuthRedisKeys.sessionPresenceLastSeenKey(), sessionId);
        return score == null ? null : score.longValue();
    }

    /**
     * 主动退出或会话过期时删除 presence 状态。
     */
    public void delete(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        String lua = """
                redis.call('del', KEYS[1])
                redis.call('zrem', KEYS[2], ARGV[1])
                return 1
                """;
        stringRedisTemplate.execute(
                new DefaultRedisScript<>(lua, Long.class),
                List.of(AuthRedisKeys.sessionPresenceKey(sessionId), AuthRedisKeys.sessionPresenceLastSeenKey()),
                sessionId
        );
    }

    /**
     * 标记会话离线。
     *
     * <p>只有当前状态是 ONLINE 时才切换为 OFFLINE，避免重复事件。</p>
     */
    public boolean markOffline(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return false;
        }
        String lua = """
                local current = redis.call('get', KEYS[1])
                if current ~= ARGV[2] then
                    return 0
                end
                redis.call('set', KEYS[1], ARGV[3], 'PX', ARGV[4])
                redis.call('zrem', KEYS[2], ARGV[1])
                return 1
                """;
        Long changed = stringRedisTemplate.execute(
                new DefaultRedisScript<>(lua, Long.class),
                List.of(AuthRedisKeys.sessionPresenceKey(sessionId), AuthRedisKeys.sessionPresenceLastSeenKey()),
                sessionId,
                ONLINE,
                OFFLINE,
                String.valueOf(PRESENCE_TTL.toMillis())
        );
        return changed != null && changed == 1L;
    }

    /**
     * 清理已经超过在线窗口的 sessionId，并返回它们的 lastSeen。
     *
     * <p>该 Lua 脚本原子完成读取过期 ZSET 成员、删除 presence、移除 ZSET。
     * 调用方会基于返回值发送 SESSION_OFFLINE 事件，异步更新 MySQL。</p>
     */
    public List<OfflineCandidate> cleanupExpiredOnlineSessionIndex(long expireBeforeMillis, long limit) {
        String lua = """
                local values = redis.call('zrangebyscore', KEYS[1], 0, ARGV[1], 'WITHSCORES', 'LIMIT', 0, ARGV[2])
                local cleaned = {}
                if #values > 0 then
                    for i = 1, #values, 2 do
                        local sessionId = values[i]
                        local lastSeen = values[i + 1]
                        local presenceKey = ARGV[3] .. sessionId
                        redis.call('zrem', KEYS[1], sessionId)
                        if redis.call('get', presenceKey) == ARGV[4] then
                            redis.call('del', presenceKey)
                        end
                        table.insert(cleaned, sessionId)
                        table.insert(cleaned, lastSeen)
                    end
                end
                return cleaned
                """;
        @SuppressWarnings("unchecked")
        List<String> values = stringRedisTemplate.execute(
                new DefaultRedisScript<>(lua, List.class),
                List.of(AuthRedisKeys.sessionPresenceLastSeenKey()),
                String.valueOf(expireBeforeMillis),
                String.valueOf(limit),
                "auth:presence:",
                ONLINE
        );
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }

        List<OfflineCandidate> result = new ArrayList<>();
        for (int index = 0; index + 1 < values.size(); index += 2) {
            String sessionId = values.get(index);
            Long lastSeenMillis = parseLong(values.get(index + 1));
            if (StringUtils.hasText(sessionId) && lastSeenMillis != null) {
                result.add(new OfflineCandidate(sessionId, lastSeenMillis));
            }
        }
        return result;
    }

    /**
     * 从在线索引中批量删除 sessionId。
     */
    public void removeOnlineIndex(Collection<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return;
        }
        stringRedisTemplate.opsForZSet().remove(AuthRedisKeys.sessionPresenceLastSeenKey(), sessionIds.toArray());
    }

    /**
     * 安全解析 long。
     */
    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Double.valueOf(value).longValue();
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * Redis 判断出的离线候选会话。
     */
    @Data
    @AllArgsConstructor
    public static class OfflineCandidate {
        private String sessionId;
        private Long lastSeenMillis;
    }
}
