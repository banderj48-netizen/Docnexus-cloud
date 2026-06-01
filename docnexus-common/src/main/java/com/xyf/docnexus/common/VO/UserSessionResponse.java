package com.xyf.docnexus.common.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户在线会话展示对象。
 *
 * <p>该对象只返回前端需要展示的安全信息，不返回 refreshTokenHash、accessJti 等敏感字段。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSessionResponse {

    /**
     * 当前会话 ID，用于退出指定设备。
     */
    private String sessionId;

    /**
     * 是否是当前浏览器正在使用的会话。
     */
    private Boolean current;

    /**
     * 是否在线。
     *
     * <p>true 表示该 session 最近仍在发送 heartbeat；
     * false 表示该 session 仍处于 ACTIVE 授权状态，但浏览器可能已经关闭或长时间未活跃。</p>
     */
    private Boolean online;

    /**
     * 客户端 IP。
     */
    private String clientIp;

    /**
     * 设备名称，后端根据 User-Agent 做轻量识别。
     */
    private String deviceName;

    /**
     * 浏览器或客户端信息。
     */
    private String userAgent;

    /**
     * 会话状态。
     */
    private String status;

    /**
     * 登录时间，毫秒时间戳。
     */
    private Long loginAtMillis;

    /**
     * 最近活跃时间，毫秒时间戳。
     */
    private Long lastActiveAtMillis;

    /**
     * refreshToken 过期时间，毫秒时间戳。
     */
    private Long refreshExpiresAtMillis;
}
