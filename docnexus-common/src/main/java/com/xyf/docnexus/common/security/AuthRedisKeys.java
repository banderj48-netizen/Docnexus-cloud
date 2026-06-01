package com.xyf.docnexus.common.security;

public class AuthRedisKeys {
    public static String sessionKey(String jwtId) {
        return "auth:session:" + jwtId;
    }

    public static String blacklistKey(String jwtId) {
        return "auth:blacklist:" + jwtId;
    }

    /**
     * 会话级刷新黑名单 Key。
     *
     * <p>accessToken 失效只靠 {@code auth:blacklist:{jti}} 和 {@code auth:session:{jti}}；
     * refreshToken 续签还必须额外看该 Key。主动退出后即使 MySQL 还没被 MQ 更新，
     * refresh 接口也会因为该 Key 存在而拒绝旧 refreshToken，避免旧会话被重新续活。</p>
     */
    public static String sessionRevokedKey(String sessionId) {
        return "auth:session:revoked:" + sessionId;
    }

    // 用来一批已经签发出去的 JWT 立刻失效，比如用户改密码、账号被封禁、权限被修改
    public static String tokenVersionKey(Long userId) {
        return "auth:user:token-version:" + userId;
    }

    /**
     * 登录用户快照缓存 Key。
     *
     * <p>用于登录接口根据用户名快速读取用户 ID、密码、角色、状态和 tokenVersion，
     * 降低高频登录时对 user_account 表的读取压力。</p>
     */
    public static String userLoginKey(String username) {
        return "auth:user:login:" + username;
    }

    public static String passwordResetPermitKey(String username) {
        return "auth:password-reset:" + username;
    }

    public static String userProfileKey(Long userId) {
        return "auth:user:profile:" + userId;
    }

    /**
     * 会话在线状态 Key。
     *
     * <p>value 为 ONLINE / OFFLINE。heartbeat 会通过 Lua 原子刷新 lastSeen ZSET 和当前 Key 的 TTL；
     * 如果会话已经是 ONLINE，不再重复触发业务状态变化，也不会写 MySQL。</p>
     */
    public static String sessionPresenceKey(String sessionId) {
        return "auth:presence:" + sessionId;
    }

    /**
     * 会话最后活跃时间 ZSET Key。
     *
     * <p>member 为 sessionId，score 为最近 heartbeat 毫秒时间戳。</p>
     */
    public static String sessionPresenceLastSeenKey() {
        return "auth:presence:lastseen";
    }

    /**
     * heartbeat 会话归属缓存 Key。
     *
     * <p>用于避免每次 heartbeat 都查询 MySQL user_session。
     * value 保存 userId 和 refreshToken 授权过期时间。</p>
     */
    public static String heartbeatSessionKey(String sessionId) {
        return "auth:heartbeat:session:" + sessionId;
    }

    /**
     * 用户会话列表缓存版本 Key。
     *
     * <p>会话列表缓存使用版本号失效，不做 Redis scan 批量删除。
     * 登录、退出、refreshToken 轮换等会影响会话列表排序或状态的动作，只需要 INCR 该版本号。</p>
     */
    public static String userSessionListVersionKey(Long userId) {
        return "auth:user:sessions:version:" + userId;
    }

    /**
     * 用户会话列表分页缓存 Key。
     */
    public static String userSessionListPageKey(Long userId, String version, Integer pageNum, Integer pageSize) {
        return "auth:user:sessions:" + userId + ":" + version + ":" + pageNum + ":" + pageSize;
    }

    /**
     * 登录用户快照缓存互斥锁 Key。
     */
    public static String userLoginLockKey(String username) {
        return "lock:auth:user:login:" + username;
    }

    /**
     * 用户资料缓存互斥锁 Key。
     */
    public static String userProfileLockKey(Long userId) {
        return "lock:auth:user:profile:" + userId;
    }

    /**
     * tokenVersion 缓存互斥锁 Key。
     */
    public static String tokenVersionLockKey(Long userId) {
        return "lock:auth:user:token-version:" + userId;
    }

    /**
     * 同设备登录互斥锁 Key。
     *
     * <p>同一用户、同一 deviceId 在高并发登录时只能有一个请求执行会话接管，
     * 避免多个浏览器同时登录时创建多个 ACTIVE 会话。</p>
     */
    public static String deviceLoginLockKey(Long userId, String deviceId) {
        return "lock:auth:device-login:" + userId + ":" + deviceId;
    }

    /**
     * 登录 IP 限流计数。
     *
     * <p>用于限制同一个 IP 在短时间内的登录尝试次数，降低暴力破解和高并发刷接口风险。</p>
     */
    public static String loginIpLimitKey(String clientIp) {
        return "auth:login:ip-limit:" + clientIp;
    }

    /**
     * 用户名维度的登录失败次数。
     */
    public static String loginFailKey(String username) {
        return "auth:login:fail:" + username;
    }

    /**
     * 用户名维度的登录锁定标记。
     */
    public static String loginLockKey(String username) {
        return "auth:login:lock:" + username;
    }

}
