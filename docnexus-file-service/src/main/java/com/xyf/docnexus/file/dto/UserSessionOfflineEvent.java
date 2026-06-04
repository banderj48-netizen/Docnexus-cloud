package com.xyf.docnexus.file.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户会话离线事件快照。
 *
 * <p>file-service 只关心 user-service 发送的 userId 和 allSessionsOffline，
 * 不反向依赖 user-service 的事件类，避免微服务之间共享业务实现类型。</p>
 */
@Data
public class UserSessionOfflineEvent {
    private String eventId;
    private Long userId;
    private String sessionId;
    private Long lastActiveAtMillis;
    private LocalDateTime offlineAt;
    private Boolean allSessionsOffline;
}
