package com.xyf.docnexus.user.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户会话关闭参数，避免 Mapper 多参数。
 *
 * <p>该对象同时服务于主动退出、refreshToken 过期、tokenVersion 失效等场景。
 * MySQL 中 status 表示授权状态，closeReason 表示具体关闭原因。</p>
 */
@Data
public class UserSessionLogoutParam {
    private String sessionId;
    private String accessJti;
    private Long userId;

    /**
     * 授权状态：ACTIVE / EXPIRED。
     */
    private String status;

    /**
     * 兼容旧字段：退出或过期时间。
     */
    private LocalDateTime logoutAt;

    /**
     * 授权会话过期时间。
     */
    private LocalDateTime expiredAt;

    /**
     * 关闭原因：LOGOUT / REFRESH_EXPIRED / REFRESH_INVALID / TOKEN_VERSION_CHANGED。
     */
    private String closeReason;

    /**
     * 会话最后活跃时间。
     *
     * <p>MQ 可能乱序或重复投递，Mapper 更新 MySQL 时只会接受比当前值更晚的时间。</p>
     */
    private LocalDateTime lastActiveAt;

    private LocalDateTime updateTime;
}
