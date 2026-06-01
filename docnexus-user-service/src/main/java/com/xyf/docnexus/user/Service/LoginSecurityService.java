package com.xyf.docnexus.user.Service;

/**
 * 登录安全服务。
 *
 * <p>负责登录前限流、账号锁定检查、登录失败计数和登录成功后的安全状态清理。</p>
 */
public interface LoginSecurityService {

    /**
     * 登录前检查当前 IP 和用户名是否允许继续尝试登录。
     */
    void checkLoginAllowed(String username, String clientIp);

    /**
     * 记录一次登录失败。
     */
    void recordLoginFailure(String username);

    /**
     * 登录成功后清理失败次数和锁定标记。
     */
    void recordLoginSuccess(String username);
}
