package com.xyf.docnexus.common.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis 中保存的 accessToken 登录态快照。
 *
 * <p>Gateway 通过 `auth:session:{jwtId}` 读取该对象，校验 token 是否仍然有效。
 * 这里保存的是轻量身份信息和安全绑定信息，不保存 refreshToken 明文。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RedisTokenSession {

    /**
     * 用户 ID。
     */
    private Long userId;

    /**
     * 用户名。
     */
    private String username;

    /**
     * 用户角色。
     */
    private String role;

    /**
     * accessToken 的唯一编号。
     */
    private String jwtId;

    /**
     * 当前 accessToken 归属的会话 ID。
     */
    private String sessionId;

    /**
     * accessToken 签发时间，毫秒时间戳。
     */
    private Long issuedAtMillis;

    /**
     * accessToken 过期时间，毫秒时间戳。
     */
    private Long expiresAtMillis;

    /**
     * refreshToken 授权过期时间，毫秒时间戳。
     */
    private Long refreshExpiresAtMillis;

    /**
     * 用户当前 tokenVersion。
     */
    private Long tokenVersion;

    /**
     * 当前 accessToken 绑定的登录 IP。
     *
     * <p>Gateway 会把请求真实 IP 与该字段比较，防止同一个 token 在其他 IP 下被复用。</p>
     */
    private String boundIp;

    /**
     * 当前会话归属的设备 ID。
     *
     * <p>该值由 user-service 根据 userId、登录 IP 和设备指纹计算，
     * 用于同设备会话接管和排查日志，不单独作为安全认证依据。</p>
     */
    private String deviceId;
}
