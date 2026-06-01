package com.xyf.docnexus.user.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户会话失效事件。
 *
 * <p>用于把主动退出、refreshToken 过期、refreshToken 非法、tokenVersion 变化等状态变更
 * 异步投递给 RocketMQ Consumer，由 Consumer 最终更新 MySQL。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionExpiredEvent {

    /**
     * 事件唯一 ID，用于日志追踪和后续幂等扩展。
     */
    private String eventId;

    /**
     * 用户 ID。
     */
    private Long userId;

    /**
     * 用户会话 ID。
     */
    private String sessionId;

    /**
     * 当前会话对应的 accessToken jti。
     */
    private String accessJti;

    /**
     * accessToken 过期时间，毫秒时间戳。
     */
    private Long accessExpiresAtMillis;

    /**
     * 关闭原因：LOGOUT / REFRESH_EXPIRED / REFRESH_INVALID / TOKEN_VERSION_CHANGED。
     */
    private String closeReason;

    /**
     * 会话最后活跃时间，毫秒时间戳。
     *
     * <p>该字段来自 Redis presence lastSeen。Consumer 落库时只会接受比 MySQL 当前值更晚的时间，
     * 避免 MQ 乱序或重复投递导致 last_active_at 被旧值覆盖。</p>
     */
    private Long lastActiveAtMillis;

    /**
     * 事件发生时间。
     */
    private LocalDateTime occurredAt;
}
