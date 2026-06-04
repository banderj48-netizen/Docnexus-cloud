package com.xyf.docnexus.user.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户会话离线事件。
 *
 * <p>该事件只表示浏览器在 heartbeat 窗口内没有继续活跃，
 * 不代表用户主动退出，也不会把授权会话 status 改为 EXPIRED。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionOfflineEvent {

    /**
     * 事件唯一 ID，用于日志追踪。
     */
    private String eventId;

    /**
     * 用户 ID。
     */
    private Long userId;

    /**
     * 会话 ID。
     */
    private String sessionId;

    /**
     * Redis lastSeen 记录的最后活跃时间，毫秒时间戳。
     */
    private Long lastActiveAtMillis;

    /**
     * 后端判定离线的时间。
     */
    private LocalDateTime offlineAt;

    /**
     * 是否该用户所有会话都已离线。
     *
     * <p>下游服务只在该字段为 true 时清理用户级缓存，避免单设备离线误伤其他在线设备。</p>
     */
    private Boolean allSessionsOffline;
}
