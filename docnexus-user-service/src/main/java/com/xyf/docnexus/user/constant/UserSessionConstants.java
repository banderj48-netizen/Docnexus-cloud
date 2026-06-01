package com.xyf.docnexus.user.constant;

/**
 * 用户会话常量。
 *
 * <p>该类集中维护 user-service 中和登录会话相关的稳定枚举值，
 * 避免在 Service、Mapper 参数、MQ 事件中重复书写字符串常量。</p>
 */
public final class UserSessionConstants {

    private UserSessionConstants() {
        // 工具常量类不允许实例化。
    }

    /**
     * 授权会话仍有效。
     */
    public static final String SESSION_STATUS_ACTIVE = "ACTIVE";

    /**
     * 授权会话已失效。
     *
     * <p>主动退出、refreshToken 过期、refreshToken 非法、tokenVersion 不一致，
     * 都统一落为 EXPIRED，具体原因写入 closeReason。</p>
     */
    public static final String SESSION_STATUS_EXPIRED = "EXPIRED";

    /**
     * 页面在线。
     */
    public static final String ONLINE_STATUS_ONLINE = "ONLINE";

    /**
     * 页面离线。
     */
    public static final String ONLINE_STATUS_OFFLINE = "OFFLINE";

    /**
     * 用户主动退出登录。
     */
    public static final String CLOSE_REASON_LOGOUT = "LOGOUT";

    /**
     * refreshToken 已经过期。
     */
    public static final String CLOSE_REASON_REFRESH_EXPIRED = "REFRESH_EXPIRED";

    /**
     * refreshToken 与数据库哈希不匹配。
     */
    public static final String CLOSE_REASON_REFRESH_INVALID = "REFRESH_INVALID";

    /**
     * tokenVersion 不一致，通常发生在修改密码、重置密码、强制下线后。
     */
    public static final String CLOSE_REASON_TOKEN_VERSION_CHANGED = "TOKEN_VERSION_CHANGED";

    /**
     * 同一用户在相同设备重新登录，新登录接管旧登录态。
     *
     * <p>该原因用于区分“用户主动退出”和“安全版本变更”，方便会话审计时判断
     * 旧浏览器为什么被踢下线。</p>
     */
    public static final String CLOSE_REASON_SAME_DEVICE_TAKEOVER = "SAME_DEVICE_TAKEOVER";

    /**
     * 未知关闭原因兜底。
     */
    public static final String CLOSE_REASON_UNKNOWN = "UNKNOWN";

    /**
     * RocketMQ 会话失效事件 Tag。
     */
    public static final String MQ_TAG_SESSION_EXPIRED = "SESSION_EXPIRED";

    /**
     * RocketMQ 会话离线事件 Tag。
     *
     * <p>浏览器普通离线只影响 online_status、offline_at 和 last_active_at，
     * 不会把授权会话 status 改为 EXPIRED。</p>
     */
    public static final String MQ_TAG_SESSION_OFFLINE = "SESSION_OFFLINE";
}
