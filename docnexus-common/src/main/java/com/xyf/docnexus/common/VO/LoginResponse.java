package com.xyf.docnexus.common.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /**
     * accessToken，前端放到 Authorization: Bearer xxx。
     */
    private String token;

    /**
     * refreshToken，只用于刷新 accessToken。
     */
    private String refreshToken;

    /**
     * 当前登录会话 ID。
     */
    private String sessionId;

    private Long userId;

    private String username;

    private String role;

    /**
     * accessToken 毫秒级过期时间。
     */
    private Long accessTokenExpiresAtMillis;

    /**
     * refreshToken 毫秒级过期时间。
     */
    private Long refreshTokenExpiresAtMillis;

    /**
     * 是否接管了同设备上的旧会话。
     *
     * <p>true 表示后端发现同一 userId + deviceId 已存在 ACTIVE 会话，
     * 本次登录复用了原 sessionId 并轮换 token，旧浏览器的 accessJti 已进入 blacklist。</p>
     */
    private Boolean sessionTakeover;

    /**
     * 会话接管提示文案。
     */
    private String takeoverMessage;
}
