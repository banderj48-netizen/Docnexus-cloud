package com.xyf.docnexus.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 登录安全策略配置。
 *
 * <p>这些参数放到配置文件中，便于开发、测试、生产环境按实际并发量调整，
 * 不需要为了修改限流阈值重新发布代码。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "docnexus.login-security")
public class LoginSecurityProperties {

    /**
     * 同一个 IP 在限流窗口内最多允许的登录尝试次数。
     */
    private Long ipMaxAttempts = 60L;

    /**
     * IP 登录限流窗口，单位：秒。
     */
    private Long ipWindowSeconds = 60L;

    /**
     * 同一个用户名在失败统计窗口内最多允许输错密码的次数。
     */
    private Long usernameMaxFailures = 5L;

    /**
     * 用户名失败次数统计窗口，单位：秒。
     */
    private Long usernameFailureWindowSeconds = 900L;

    /**
     * 用户名达到失败次数上限后的锁定时间，单位：秒。
     */
    private Long usernameLockSeconds = 900L;
}
