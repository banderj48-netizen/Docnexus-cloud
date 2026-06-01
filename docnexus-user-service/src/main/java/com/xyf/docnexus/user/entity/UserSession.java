package com.xyf.docnexus.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户登录会话表实体。
 */
@Data
public class UserSession {
    private Long id;
    private String sessionId;
    private Long userId;
    private String refreshTokenHash;
    private String accessJti;
    private Long tokenVersion;
    private String deviceId;
    private String deviceName;
    private String clientIp;
    private String userAgent;
    private String status;
    private LocalDateTime loginAt;
    private LocalDateTime lastActiveAt;
    private LocalDateTime accessExpiresAt;
    private LocalDateTime refreshExpiresAt;
    private LocalDateTime logoutAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String onlineStatus;
    private LocalDateTime offlineAt;
    private LocalDateTime expiredAt;
    private String closeReason;
}
